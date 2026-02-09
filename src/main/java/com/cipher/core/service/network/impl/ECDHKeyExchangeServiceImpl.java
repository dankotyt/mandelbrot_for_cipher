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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
    private boolean serverRunning = false;
    private final ExecutorService connectionPool = Executors.newCachedThreadPool();

    public ECDHKeyExchangeServiceImpl() {
        generateNewKeys();
    }

    @PostConstruct
    public void init() {
        activeConnections.clear();
        peerKeys.clear();
        connectionInProgress.clear();
        startKeyExchangeServer();
    }

    @PreDestroy
    public void cleanup() {
        serverRunning = false;
        closeAllConnections();
        activeConnections.clear();
        peerKeys.clear();
        connectionInProgress.clear();

        if (keyExchangeServerSocket != null) {
            try {
                keyExchangeServerSocket.close();
            } catch (IOException e) {
                log.warn("Ошибка закрытия сервера обмена ключами: {}", e.getMessage());
            }
        }

        connectionPool.shutdown();
    }

    /**
     * Запускает сервер для обработки входящих соединений обмена ключами
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
     * Обрабатывает входящее соединение для обмена ключами.
     * Логика: получаем -> отправляем.
     * @param clientSocket сокет клиента
     */
    private void handleKeyExchangeConnection(Socket clientSocket) {
        connectionPool.execute(() -> {
            String clientIp = clientSocket.getInetAddress().getHostAddress();
            log.info("Входящее соединение для обмена ключами от: {}", clientIp);

            try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                // Проверяем, не является ли это сообщением об инвалидации ключей
                try {
                    String message = in.readUTF();
                    if (NetworkConstants.KEY_INVALIDATION_MESSAGE.equals(message)) {
                        log.info("Получена инвалидация ключей от: {}", clientIp);
                        removePeerKeys(clientIp);
                        closeConnection(clientSocket.getInetAddress());
                        return;
                    }
                } catch (Exception e) {
                    // Не UTF сообщение, продолжаем как обычный обмен ключами
                    // Возвращаемся к началу потока
                    clientSocket.getInputStream().reset();
                }

                // Получаем публичный ключ клиента
                int keyLength = in.readInt();
                if (keyLength <= 0 || keyLength > 10000) {
                    throw new IOException("Неверная длина ключа: " + keyLength);
                }

                byte[] clientPublicKeyBytes = new byte[keyLength];
                in.readFully(clientPublicKeyBytes);

                // Отправляем наш публичный ключ
                ECDHKeyExchange ourKeys = getCurrentKeys();
                byte[] ourPublicKey = ourKeys.getPublicKeyBytes();
                out.writeInt(ourPublicKey.length);
                out.write(ourPublicKey);
                out.flush();

                // Вычисляем общий секрет
                ourKeys.computeSharedSecret(clientPublicKeyBytes);

                // Сохраняем соединение
                addConnection(clientSocket.getInetAddress(), ourKeys);

                log.info("Обмен ключами завершен с: {}", clientIp);

            } catch (IOException e) {
                log.error("Ошибка обмена ключами с {}: {}", clientIp, e.getMessage());
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

                if (ourKeys == null) {
                    log.error("No current keys available");
                    return false;
                }

                // Отправляем наш публичный ключ
                byte[] ourPublicKey = ourKeys.getPublicKeyBytes();
                out.writeInt(ourPublicKey.length);
                out.write(ourPublicKey);
                out.flush();

                // Получаем публичный ключ пира
                int keyLength = in.readInt();
                if (keyLength <= 0 || keyLength > 10000) {
                    throw new IOException("Invalid key length: " + keyLength);
                }

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

    @Override
    public CompletableFuture<Boolean> performKeyExchangeAsync(InetAddress peerAddress) {
        return CompletableFuture.supplyAsync(() -> performKeyExchange(peerAddress))
                .orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    log.error("Асинхронный ECDH обмен ключами не удался с {}: {}",
                            peerAddress.getHostAddress(), throwable.getMessage());
                    connectionInProgress.remove(peerAddress);
                    return false;
                });
    }

    @Override
    public void savePeerKeys(String peerIp, ECDHKeyExchange keys) {
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
            peerInfo.updateKeyExchangeTime();

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
            peerInfo.updateKeyExchangeTime();

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
    public void processIncomingKeyExchange(InetAddress peerAddress, byte[] publicKey) {
        updatePeerConnection(peerAddress);
    }

    @Override
    public void sendKeyInvalidation(InetAddress peerAddress) {
        new Thread(() -> {
            log.info("Sending key invalidation to {}", peerAddress.getHostAddress());

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(peerAddress, NetworkConstants.KEY_EXCHANGE_PORT), 5000);
                socket.setSoTimeout(5000);

                try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                    out.writeUTF(NetworkConstants.KEY_INVALIDATION_MESSAGE);
                    out.flush();
                    log.info("Key invalidation sent to {}", peerAddress.getHostAddress());
                }

            } catch (IOException e) {
                log.warn("Failed to send key invalidation to {}: {}",
                        peerAddress.getHostAddress(), e.getMessage());
            }
        }, "Key-Invalidation-Sender").start();
    }
}
