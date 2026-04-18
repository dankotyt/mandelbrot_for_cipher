package com.dankotyt.core.service.network.impl;

import com.dankotyt.core.model.ConnectionStatus;
import com.dankotyt.core.model.ECDHKeyPair;
import com.dankotyt.core.model.PeerInfo;
import com.dankotyt.core.service.encryption.ECDHService;
import com.dankotyt.core.service.network.CryptoKeyManager;
import com.dankotyt.core.utils.NetworkConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class ECDHCryptoKeyManagerImpl implements CryptoKeyManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECDHCryptoKeyManagerImpl.class);

    private final ECDHService ecdhService;
    private final AtomicReference<ECDHKeyPair> currentKeyExchange = new AtomicReference<>();
    private final Map<InetAddress, PeerInfo> activeConnections = new ConcurrentHashMap<>();
    private final Map<InetAddress, Boolean> connectionInProgress = new ConcurrentHashMap<>();
    private final Map<String, ECDHKeyPair> peerKeys = new ConcurrentHashMap<>();

    private ServerSocket keyInvalidationServerSocket;
    private boolean serverRunning = false;
    private final ExecutorService connectionPool = Executors.newCachedThreadPool();
    private volatile InetAddress currentPeer;

    public ECDHCryptoKeyManagerImpl(ECDHService ecdhService) {
        this.ecdhService = ecdhService;
        generateNewKeys();
    }

    @PostConstruct
    public void init() {
        activeConnections.clear();
        peerKeys.clear();
        connectionInProgress.clear();
        startKeyInvalidationServer();
    }

    @PreDestroy
    public void cleanup() {
        serverRunning = false;
        closeAllConnections();
        activeConnections.clear();
        peerKeys.clear();
        connectionInProgress.clear();

        closeServerSocket(keyInvalidationServerSocket, "инвалидации ключей");

        connectionPool.shutdown();
        try {
            if (!connectionPool.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                connectionPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            connectionPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void closeServerSocket(ServerSocket socket, String serverName) {
        if (socket != null) {
            try {
                socket.close();
                log.info("Сервер {} остановлен", serverName);
            } catch (IOException e) {
                log.warn("Ошибка закрытия сервера {}: {}", serverName, e.getMessage());
            }
        }
    }

    /**
     * Запускает сервер для обработки сообщений об инвалидации ключей
     * Использует текстовый протокол: [UTF строка]
     */
    private void startKeyInvalidationServer() {
        new Thread(() -> {
            try {
                keyInvalidationServerSocket = new ServerSocket(NetworkConstants.KEY_INVALIDATION_PORT);
                log.info("Сервер инвалидации ключей запущен на порту {}", NetworkConstants.KEY_INVALIDATION_PORT);

                while (serverRunning) {
                    Socket clientSocket = keyInvalidationServerSocket.accept();
                    handleKeyInvalidationConnection(clientSocket);
                }
            } catch (IOException e) {
                if (serverRunning) {
                    log.error("Ошибка сервера инвалидации ключей: {}", e.getMessage());
                }
            }
        }, "KeyInvalidation-Server").start();
    }

    /**
     * Обрабатывает входящее соединение для инвалидации ключей
     */
    private void handleKeyInvalidationConnection(Socket clientSocket) {
        connectionPool.execute(() -> {
            String clientIp = clientSocket.getInetAddress().getHostAddress();

            try (DataInputStream in = new DataInputStream(clientSocket.getInputStream())) {
                byte messageType = in.readByte();

                if (messageType == NetworkConstants.MSG_KEY_INVALIDATION) {
                    log.info("Получена инвалидация ключей от: {}", clientIp);
                    removePeerKeys(clientIp);
                    closeConnection(clientSocket.getInetAddress());
                }
            } catch (IOException e) {
                log.warn("Ошибка обработки инвалидации от {}: {}", clientIp, e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    log.warn("Ошибка закрытия сокета инвалидации: {}", e.getMessage());
                }
            }
        });
    }

    @Override
    public ECDHKeyPair getCurrentKeys() {
        return currentKeyExchange.get();
    }

    @Override
    public byte[] getMasterSeedFromDH(InetAddress peerAddress) {
        if (peerAddress == null) {
            throw new IllegalStateException("Не установлен активный пир.");
        }
        try {
            PeerInfo peerInfo = activeConnections.get(peerAddress);
            if (peerInfo != null && peerInfo.getEcdhKeys() != null) {
                byte[] sharedSecret = peerInfo.getEcdhKeys().getSharedSecretBytes();
                if (sharedSecret != null && sharedSecret.length > 0) {
                    log.debug("ECDH мастер-сид получен для пира: {}, длина: {} байт",
                            peerAddress.getHostAddress(), sharedSecret.length);
                    return sharedSecret;
                }
            }

            throw new IllegalStateException("Нет активного ECDH соединения или общего секрета для пира: " + peerAddress);

        } catch (Exception e) {
            log.error("Ошибка при получении ECDH мастер-сида для {}: {}",
                    peerAddress.getHostAddress(), e.getMessage(), e);
            throw new RuntimeException("Не удалось получить ECDH мастер-сид", e);
        }
    }

    private void savePeerKeys(String peerIp, ECDHKeyPair keys) {
        peerKeys.put(peerIp, keys);
        LOGGER.info("Сохранены ключи для пира: {}", peerIp);
    }

    @Override
    public ECDHKeyPair getPeerKeys(String peerIp) {
        return peerKeys.get(peerIp);
    }

    @Override
    public boolean hasKeysForPeer(String peerIp) {
        return peerKeys.containsKey(peerIp);
    }

    @Override
    public void removePeerKeys(String peerIp) {
        ECDHKeyPair removed = peerKeys.remove(peerIp);
        if (removed != null) {
            removed.invalidate();
            log.info("Удалены ключи для пира: {}", peerIp);
        }
    }

    private void updatePeerConnection(InetAddress peerAddress) {
        try {
            ECDHKeyPair currentKeys = currentKeyExchange.get();
            if (currentKeys == null) {
                log.warn("Текущие ECDH ключи не найдены, генерируем новые");
                generateNewKeys();
                currentKeys = currentKeyExchange.get();
            }

            PeerInfo peerInfo = activeConnections.computeIfAbsent(peerAddress, PeerInfo::new);
            peerInfo.setEcdhKeys(currentKeys);
            peerInfo.setStatus(ConnectionStatus.CONNECTED);
            peerInfo.updateLastSeen();

            log.info("ECDH информация о пире обновлена: {}", peerAddress.getHostAddress());

        } catch (Exception e) {
            log.error("Ошибка при обновлении ECDH информации о пире {}: {}",
                    peerAddress.getHostAddress(), e.getMessage(), e);
        }
    }

    @Override
    public void generateNewKeys() {
        ECDHKeyPair newKeys = ecdhService.generateKeyPair();
        currentKeyExchange.set(newKeys);
        log.info("Generated new ECDH keys");

        for (InetAddress peerAddress : activeConnections.keySet() ) {
            sendKeyInvalidation(peerAddress);
        }
    }

    @Override
    public void addConnection(InetAddress peerAddress, ECDHKeyPair keys) {
        if (connectionInProgress.putIfAbsent(peerAddress, true) != null) {
            log.debug("Входящее подключение от {} уже обрабатывается", peerAddress.getHostAddress());
            return;
        }

        try {
            if (keys == null) {
                throw new IllegalArgumentException("Expected ECDHKeyExchange instance");
            }

            savePeerKeys(peerAddress.getHostAddress(), keys);

            if (isConnectedTo(peerAddress)) {
                log.debug("Подключение к {} уже существует, обновляем ключи", peerAddress.getHostAddress());
            }

            PeerInfo peerInfo = new PeerInfo(peerAddress);
            peerInfo.setEcdhKeys(keys);
            peerInfo.setStatus(ConnectionStatus.CONNECTED);
            peerInfo.updateLastSeen();

            activeConnections.put(peerAddress, peerInfo);
            log.info("Добавлено ECDH соединение с: {}", peerAddress.getHostAddress());

        } catch (Exception e) {
            log.error("Ошибка при добавлении ECDH соединения с {}: {}",
                    peerAddress.getHostAddress(), e.getMessage());
        } finally {
            connectionInProgress.remove(peerAddress);
        }
    }

    @Override
    public void closeConnection(InetAddress peerAddress) {
        PeerInfo removed = activeConnections.remove(peerAddress);
        if (removed != null) {
            sendKeyInvalidation(peerAddress);
            log.info("Closed ECDH connection to: {}", peerAddress);
        }
    }

    @Override
    public void closeAllConnections() {
        if (!activeConnections.isEmpty()) {
            log.info("Closing all ECDH connections...");

            for (InetAddress peerAddress : activeConnections.keySet() ) {
                sendKeyInvalidation(peerAddress);
            }

            activeConnections.clear();
            log.info("All ECDH connections closed");
        }
    }

    @Override
    public Map<InetAddress, String> getActiveConnections() {
        Map<InetAddress, String> result = new ConcurrentHashMap<>();
        activeConnections.forEach((address, info) ->
                result.put(address, info.getStatus().name()));
        return result;
    }

    /**
     * Устанавливает текущего активного пира.
     *
     * @param peerAddress IP-адрес пира, или null для сброса
     */
    @Override
    public void setConnectedPeer(InetAddress peerAddress) {
        this.currentPeer = peerAddress;
    }

    /**
     * Возвращает текущего активного пира.
     *
     * @return InetAddress активного пира или null
     */
    @Override
    public InetAddress getConnectedPeer() {
        return currentPeer;
    }

    @Override
    public boolean isConnectedTo(InetAddress peerAddress) {
        return activeConnections.containsKey(peerAddress) &&
                activeConnections.get(peerAddress).getStatus() == ConnectionStatus.CONNECTED;
    }

    @Override
    public String getConnectionStatus(InetAddress peerAddress) {
        PeerInfo info = activeConnections.get(peerAddress);
        return info != null ? info.getStatus().name() : "DISCONNECTED";
    }

    @Override
    public void sendKeyInvalidation(InetAddress peerAddress) {
        CompletableFuture.runAsync(() -> {
            log.info("Отправка инвалидации ключей для: {}", peerAddress.getHostAddress());

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(peerAddress, NetworkConstants.KEY_INVALIDATION_PORT), 5000);
                socket.setSoTimeout(5000);

                try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                    out.writeByte(NetworkConstants.MSG_KEY_INVALIDATION);
                    out.flush();
                    log.info("Инвалидация ключей отправлена для: {}", peerAddress.getHostAddress());
                }
            } catch (IOException e) {
                log.warn("Не удалось отправить инвалидацию ключей для {}: {}",
                        peerAddress.getHostAddress(), e.getMessage());
            }
        }, connectionPool);
    }
}
