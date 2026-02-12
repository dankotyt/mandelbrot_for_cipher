package com.cipher.core.service.network.impl;

import com.cipher.client.utils.NetworkConstants;
import com.cipher.core.model.ConnectionStatus;
import com.cipher.core.model.ECDHKeyExchange;
import com.cipher.core.model.PeerInfo;
import com.cipher.core.service.network.KeyExchangeService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class ECDHKeyExchangeServiceImpl implements KeyExchangeService {
    private static final Logger logger = LoggerFactory.getLogger(ECDHKeyExchangeServiceImpl.class);

    private final AtomicReference<ECDHKeyExchange> currentKeyExchange = new AtomicReference<>();
    private final Map<InetAddress, PeerInfo> activeConnections = new ConcurrentHashMap<>();
    private final Map<InetAddress, Boolean> connectionInProgress = new ConcurrentHashMap<>();
    private final Map<String, ECDHKeyExchange> peerKeys = new ConcurrentHashMap<>();
    private final Map<String, Object> peerLocks = new ConcurrentHashMap<>();

    private ServerSocket keyExchangeServerSocket;
    private ServerSocket keyInvalidationServerSocket;
    private boolean serverRunning = false;
    private final ExecutorService connectionPool = Executors.newCachedThreadPool();
    private volatile InetAddress currentPeer;

    public ECDHKeyExchangeServiceImpl() {
        generateNewKeys();
    }

    @PostConstruct
    public void init() {
        activeConnections.clear();
        peerKeys.clear();
        connectionInProgress.clear();
        startKeyExchangeServer();
        startKeyInvalidationServer();
    }

    @PreDestroy
    public void cleanup() {
        serverRunning = false;
        closeAllConnections();
        activeConnections.clear();
        peerKeys.clear();
        connectionInProgress.clear();

        closeServerSocket(keyExchangeServerSocket, "обмена ключами");
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
                log.info("✅ Сервер {} остановлен", serverName);
            } catch (IOException e) {
                log.warn("❌ Ошибка закрытия сервера {}: {}", serverName, e.getMessage());
            }
        }
    }

    /**
     * Запускает сервер для обработки входящих соединений обмена ключами
     * Использует бинарный протокол: [int длина][byte[] ключ]
     */
    private void startKeyExchangeServer() {
        new Thread(() -> {
            try {
                keyExchangeServerSocket = new ServerSocket(NetworkConstants.KEY_EXCHANGE_PORT);
                serverRunning = true;
                log.info("✅ Сервер обмена ключами запущен на порту {}", NetworkConstants.KEY_EXCHANGE_PORT);

                while (serverRunning) {
                    Socket clientSocket = keyExchangeServerSocket.accept();
                    log.info("🔑 Принято входящее соединение для обмена ключами от: {}",
                            clientSocket.getInetAddress().getHostAddress());

                    handleKeyExchangeConnection(clientSocket);
                }
            } catch (IOException e) {
                if (serverRunning) {
                    log.error("❌ Ошибка сервера обмена ключами: {}", e.getMessage());
                }
            }
        }, "KeyExchange-Server").start();
    }

    /**
     * Запускает сервер для обработки сообщений об инвалидации ключей
     * Использует текстовый протокол: [UTF строка]
     */
    private void startKeyInvalidationServer() {
        new Thread(() -> {
            try {
                keyInvalidationServerSocket = new ServerSocket(NetworkConstants.KEY_INVALIDATION_PORT);
                log.info("✅ Сервер инвалидации ключей запущен на порту {}", NetworkConstants.KEY_INVALIDATION_PORT);

                while (serverRunning) {
                    Socket clientSocket = keyInvalidationServerSocket.accept();
                    handleKeyInvalidationConnection(clientSocket);
                }
            } catch (IOException e) {
                if (serverRunning) {
                    log.error("❌ Ошибка сервера инвалидации ключей: {}", e.getMessage());
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
                    log.info("🔴 Получена инвалидация ключей от: {}", clientIp);
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

    /**
     * Обрабатывает входящее соединение для обмена ключами.
     * Логика: получаем -> отправляем.
     * @param clientSocket сокет клиента
     */
    private void handleKeyExchangeConnection(Socket clientSocket) {
        connectionPool.execute(() -> {
            String clientIp = clientSocket.getInetAddress().getHostAddress();
            log.info("🔄 Входящее соединение для обмена ключами от: {}", clientIp);

            try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                // Получаем публичный ключ клиента
                byte messageType = in.readByte();
                if (messageType != NetworkConstants.MSG_KEY_EXCHANGE) {
                    log.warn("Expected MSG_KEY_EXCHANGE, got: {}", messageType);
                    return;
                }

                int keyLength = in.readInt();
                if (keyLength <= 0 || keyLength > 10000) {
                    throw new IOException("Неверная длина ключа: " + keyLength);
                }

                byte[] clientPublicKeyBytes = new byte[keyLength];
                in.readFully(clientPublicKeyBytes);

                // Отправляем наш публичный ключ
                ECDHKeyExchange ourKeys = getCurrentKeys();
                byte[] ourPublicKey = ourKeys.getPublicKeyBytes();

                out.writeByte(NetworkConstants.MSG_KEY_EXCHANGE);
                out.writeInt(ourPublicKey.length);
                out.write(ourPublicKey);
                out.flush();

                // Вычисляем общий секрет
                ourKeys.computeSharedSecret(clientPublicKeyBytes);

                // Сохраняем соединение
                addConnection(clientSocket.getInetAddress(), ourKeys);

                log.info("✅ Обмен ключами завершен с: {}", clientIp);

            } catch (IOException e) {
                log.error("❌ Ошибка обмена ключами с {}: {}", clientIp, e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    log.warn("Ошибка закрытия сокета: {}", e.getMessage());
                }
            }
        });
    }

    @Override
    public ECDHKeyExchange getCurrentKeys() {
        return currentKeyExchange.get();
    }

    @Override
    public byte[] getMasterSeedFromDH(InetAddress peerAddress) {
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

    @Override
    public boolean performKeyExchange(InetAddress peerAddress) {
        String peerIp = peerAddress.getHostAddress();
        Object lock = peerLocks.computeIfAbsent(peerIp, k -> new Object());

        synchronized (lock) {
            if (connectionInProgress.putIfAbsent(peerAddress, true) != null) {
                log.debug("Подключение к {} уже выполняется, пропускаем", peerIp);
                return false;
            }

            try {
                log.info("Выполнение ECDH обмена ключами с: {}", peerIp);

                if (isConnectedTo(peerAddress)) {
                    log.debug("Уже подключены к {}, пропускаем обмен ключами", peerIp);
                    return true;
                }

                ECDHKeyExchange ourKeys = currentKeyExchange.get();
                boolean success = keyExchange(peerAddress, ourKeys);

                if (success) {
                    savePeerKeys(peerIp, ourKeys);
                    updatePeerConnection(peerAddress);
                    log.info("✅ ECDH обмен ключами успешно завершен с: {}", peerIp);
                } else {
                    log.error("❌ ECDH обмен ключами не удался с: {}", peerIp);
                }

                return success;

            } catch (Exception e) {
                log.error("❌ Ошибка при выполнении ECDH обмена ключами с {}: {}", peerIp, e.getMessage(), e);
                return false;
            } finally {
                connectionInProgress.remove(peerAddress);
            }
        }
    }

    /**
     * Инициация соединения
     * Логика: отправляем -> получаем
    */
    private boolean keyExchange(InetAddress peerAddress, ECDHKeyExchange ourKeys) {
        log.info("Initiating key exchange with {}", peerAddress.getHostAddress());

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(peerAddress, NetworkConstants.KEY_EXCHANGE_PORT), 10000);
            socket.setSoTimeout(30000);

            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {

                out.writeByte(NetworkConstants.MSG_KEY_EXCHANGE);

                // Отправляем наш публичный ключ
                byte[] ourPublicKey = ourKeys.getPublicKeyBytes();
                out.writeInt(ourPublicKey.length);
                out.write(ourPublicKey);
                out.flush();

                byte responseType = in.readByte();
                if (responseType != NetworkConstants.MSG_KEY_EXCHANGE) {
                    log.error("Expected MSG_KEY_EXCHANGE response, got: {}", responseType);
                    return false;
                }

                int keyLength = in.readInt();
                byte[] peerPublicKeyBytes = new byte[keyLength];
                in.readFully(peerPublicKeyBytes);

                // Вычисляем общий секрет
                ourKeys.computeSharedSecret(peerPublicKeyBytes);

                log.info("Key exchange completed successfully with {}", peerAddress.getHostAddress());
                return true;

            } catch (SocketTimeoutException e) {
                log.error("Key exchange timeout with {}: {}", peerAddress.getHostAddress(), e.getMessage());
                return false;
            }

        } catch (IOException e) {
            log.error("Key exchange failed with {}: {}", peerAddress.getHostAddress(), e.getMessage());
            return false;
        }
    }

    private void savePeerKeys(String peerIp, ECDHKeyExchange keys) {
        peerKeys.put(peerIp, keys);
        logger.info("Сохранены ключи для пира: {}", peerIp);
    }

    @Override
    public ECDHKeyExchange getPeerKeys(String peerIp) {
        return peerKeys.get(peerIp);
    }

    @Override
    public boolean hasKeysForPeer(String peerIp) {
        return peerKeys.containsKey(peerIp);
    }

    @Override
    public void removePeerKeys(String peerIp) {
        ECDHKeyExchange removed = peerKeys.remove(peerIp);
        if (removed != null) {
            removed.invalidate();
            log.info("Удалены ключи для пира: {}", peerIp);
        }
    }

    private void updatePeerConnection(InetAddress peerAddress) {
        try {
            ECDHKeyExchange currentKeys = currentKeyExchange.get();
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
        ECDHKeyExchange newKeys = new ECDHKeyExchange();
        currentKeyExchange.set(newKeys);
        log.info("Generated new ECDH keys");

        for (InetAddress peerAddress : activeConnections.keySet() ) {
            sendKeyInvalidation(peerAddress);
        }
    }

    @Override
    public void addConnection(InetAddress peerAddress, ECDHKeyExchange keys) {
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
            log.info("📤 Отправка инвалидации ключей для: {}", peerAddress.getHostAddress());

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(peerAddress, NetworkConstants.KEY_INVALIDATION_PORT), 5000);
                socket.setSoTimeout(5000);

                try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                    out.writeByte(NetworkConstants.MSG_KEY_INVALIDATION);
                    out.flush();
                    log.info("✅ Инвалидация ключей отправлена для: {}", peerAddress.getHostAddress());
                }
            } catch (IOException e) {
                log.warn("⚠️ Не удалось отправить инвалидацию ключей для {}: {}",
                        peerAddress.getHostAddress(), e.getMessage());
            }
        }, connectionPool);
    }
}
