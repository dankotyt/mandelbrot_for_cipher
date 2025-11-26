package com.cipher.client.service.chat.impl;

import com.cipher.client.service.chat.ChatService;
import com.cipher.client.service.chat.P2PConnectionManager;
import com.cipher.client.utils.ChatEncryptionUtil;
import com.cipher.common.dto.chat.ChatMessageDTO;
import com.cipher.common.utils.NetworkConstants;
import com.cipher.core.service.network.ConnectionManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@Scope("singleton")
public class P2PChatServiceImpl implements ChatService {

    private Socket connectedSocket;
    private ServerSocket serverSocket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;

    private ExecutorService messageListener;
    private ExecutorService connectionListener;
    private final List<ChatListener> listeners = new ArrayList<>();
    private final ChatEncryptionUtil encryptionUtil;
    private final P2PConnectionManager p2pConnectionManager;
    private final ConnectionManager connectionManager;

    private volatile boolean connected = false;
    private volatile boolean listening = false;
    private String connectedPeerIp;
    private int chatPort = NetworkConstants.CHAT_PORT;

    public P2PChatServiceImpl(ChatEncryptionUtil encryptionUtil, ConnectionManager connectionManager) {
        this.encryptionUtil = encryptionUtil;
        this.connectionManager = connectionManager;
        this.messageListener = Executors.newSingleThreadExecutor();
        this.connectionListener = Executors.newSingleThreadExecutor();
        this.p2pConnectionManager = new P2PConnectionManager();
    }

    @PostConstruct
    public void init() {
        startListening(NetworkConstants.CHAT_PORT);
    }

    @PreDestroy
    public void cleanup() {
        disconnect();
        if (messageListener != null) {
            messageListener.shutdownNow();
        }
        if (connectionListener != null) {
            connectionListener.shutdownNow();
        }
    }

    @Override
    public boolean connectToPeer(String peerIp) {
        log.info("🔄 Установка P2P соединения с: {}", peerIp);

        // Проверяем, не подключены ли уже
        if (connected && peerIp.equals(connectedPeerIp)) {
            log.info("Уже подключены к: {}", peerIp);
            return true;
        }

        // Простая детерминированная логика по IP
        boolean shouldConnectAsClient = p2pConnectionManager.shouldConnectAsClient(peerIp);

        boolean success;
        if (shouldConnectAsClient) {
            log.info("🎯 Роль: КЛИЕНТ - инициируем подключение к {}", peerIp);
            success = connectAsClient(peerIp);
        } else {
            log.info("🎯 Роль: СЕРВЕР - ожидаем подключения от {}", peerIp);
            success = waitForIncomingConnection(peerIp, 10000);
        }

        // СОХРАНЯЕМ ПИРА В CONNECTION MANAGER ПРИ УСПЕШНОМ ПОДКЛЮЧЕНИИ
        if (success && connectedPeerIp != null) {
            try {
                InetAddress peerAddress = InetAddress.getByName(connectedPeerIp);
                connectionManager.setConnectedPeer(peerAddress);
                log.info("💾 Сохранен пир в ConnectionManager: {}", connectedPeerIp);
            } catch (Exception e) {
                log.error("Ошибка сохранения пира в ConnectionManager: {}", e.getMessage());
            }
        }

        return success;
    }

    private boolean smartConnect(String peerIp) {
        // Сначала пробуем по детерминированной логике
        boolean shouldConnectAsClient = p2pConnectionManager.shouldConnectAsClient(peerIp);

        if (shouldConnectAsClient) {
            // Пробуем как клиент
            if (connectAsClient(peerIp)) {
                return true;
            }
            // Если не удалось как клиент - пробуем как сервер
            log.info("🔄 Fallback: не удалось как клиент, пробуем как сервер");
            return waitForIncomingConnection(peerIp, 8000);
        } else {
            // Пробуем как сервер
            if (waitForIncomingConnection(peerIp, 8000)) {
                return true;
            }
            // Если не удалось как сервер - пробуем как клиент
            log.info("🔄 Fallback: не удалось как сервер, пробуем как клиент");
            return connectAsClient(peerIp);
        }
    }

    private boolean connectAsClient(String peerIp) {
        try {
            if (connected) {
                disconnect();
            }

            log.info("Подключение к пиру {}:{}", peerIp, chatPort);

            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(peerIp, chatPort), 10000);
            socket.setSoTimeout(0);
            connectedSocket = socket;

            setupStreams(connectedSocket);

            connected = true;
            connectedPeerIp = peerIp;

            startMessageListening();
            notifyConnectionStatusChanged(true, "Подключен к " + peerIp);

            log.info("✅ Успешно подключились как клиент к: {}", peerIp);
            return true;

        } catch (IOException e) {
            log.warn("❌ Не удалось подключиться как клиент к {}: {}", peerIp, e.getMessage());
            // При неудаче пробуем подождать входящего подключения
            return waitForIncomingConnection(peerIp, 10000);
        }
    }

    /**
     * Ожидание входящего подключения как сервер
     */
    private boolean waitForIncomingConnection(String expectedPeerIp, long timeoutMs) {
        log.info("⏳ Ожидаем входящего подключения от: {} (таймаут: {} мс)", expectedPeerIp, timeoutMs);

        final long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutMs && !connected) {
            if (connected && expectedPeerIp.equals(connectedPeerIp)) {
                log.info("✅ Получено входящее подключение от: {}", expectedPeerIp);
                return true;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        boolean success = connected && expectedPeerIp.equals(connectedPeerIp);
        if (!success) {
            log.warn("⏰ Таймаут ожидания подключения от: {}", expectedPeerIp);
            p2pConnectionManager.resetAttempts(expectedPeerIp);
        }
        return success;
    }

    private boolean isLocalAddress(String ip) {
        try {
            String localIp = InetAddress.getLocalHost().getHostAddress();
            return ip.equals(localIp) ||
                    ip.equals("127.0.0.1") ||
                    ip.equals("localhost") ||
                    NetworkInterface.getByInetAddress(InetAddress.getByName(ip)) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void startListening(int port) {
        try {
            this.chatPort = port;
            if (listening && serverSocket != null && !serverSocket.isClosed()) {
                log.info("Уже прослушиваем порт {}", port);
                return;
            }

            serverSocket = new ServerSocket(port);
            listening = true;

            if (connectionListener.isShutdown()) {
                log.warn("ConnectionListener pool shutdown, recreating...");
                connectionListener = Executors.newSingleThreadExecutor();
            }

            connectionListener.execute(this::acceptConnections);
            log.info("Прослушивание входящих подключений на порту {}", port);

        } catch (IOException e) {
            log.error("Ошибка запуска прослушивания на порту {}: {}", port, e.getMessage());
            notifyError("Ошибка запуска сервера: " + e.getMessage());
        }
    }

    private void acceptConnections() {
        while (listening && !Thread.currentThread().isInterrupted()) {
            try {
                Socket incomingSocket = serverSocket.accept();
                String peerIp = incomingSocket.getInetAddress().getHostAddress();

                log.info("Входящее подключение от: {}", peerIp);
                notifyIncomingConnection(peerIp);

                handleIncomingConnection(incomingSocket, peerIp);

            } catch (IOException e) {
                if (listening) {
                    log.error("Ошибка принятия подключения: {}", e.getMessage());
                }
                break;
            }
        }
    }

    private void handleIncomingConnection(Socket socket, String peerIp) {
        try {
            connectedSocket = socket;
            setupStreams(connectedSocket);

            connected = true;
            connectedPeerIp = peerIp;

            startMessageListening();
            notifyConnectionStatusChanged(true, "Подключен к " + peerIp);

            // СОХРАНЯЕМ ПИРА В CONNECTION MANAGER
            try {
                InetAddress peerAddress = InetAddress.getByName(peerIp);
                connectionManager.setConnectedPeer(peerAddress);
                log.info("💾 Сохранен пир (входящее подключение) в ConnectionManager: {}", peerIp);
            } catch (Exception e) {
                log.error("Ошибка сохранения пира в ConnectionManager: {}", e.getMessage());
            }

            log.info("✅ Принято входящее подключение от: {}", peerIp);
            notifyIncomingChatConnection(peerIp);

        } catch (IOException e) {
            log.error("Ошибка обработки входящего подключения: {}", e.getMessage());
            notifyError("Ошибка входящего подключения");
        }
    }

    private void notifyIncomingChatConnection(String peerIp) {
        for (ChatListener listener : listeners) {
            // Используем рефлексию для вызова метода, если он существует
            try {
                Method method = listener.getClass().getMethod("onIncomingChatConnection", String.class);
                method.invoke(listener, peerIp);
            } catch (NoSuchMethodException e) {
                // Метод не существует - игнорируем
            } catch (Exception e) {
                log.error("Ошибка уведомления о входящем чате: {}", e.getMessage());
            }
        }
    }

    private void setupStreams(Socket socket) throws IOException {
        outputStream = new ObjectOutputStream(socket.getOutputStream());
        inputStream = new ObjectInputStream(socket.getInputStream());
    }

    private void startMessageListening() {
        if (messageListener.isShutdown()) {
            messageListener = Executors.newSingleThreadExecutor();
        }
        messageListener.execute(() -> {
            try {
                while (connected && !connectedSocket.isClosed()) {
                    Object receivedObject = inputStream.readObject();

                    if (receivedObject instanceof ChatMessageDTO encryptedMessage) {
                        try {
                            ChatMessageDTO decryptedMessage = encryptionUtil.decryptMessage(encryptedMessage, connectedPeerIp);
                            handleReceivedMessage(decryptedMessage);
                        } catch (Exception e) {
                            log.error("Ошибка дешифрования сообщения от {}: {}", connectedPeerIp, e.getMessage());
                            notifyError("Ошибка дешифрования сообщения");
                        }
                    } else {
                        log.warn("Получен неизвестный объект: {}", receivedObject.getClass().getName());
                    }
                }
            } catch (IOException e) {
                if (connected) {
                    log.error("Ошибка чтения сообщения: {}", e.getMessage());
                    notifyError("Ошибка соединения");
                    disconnect();
                }
            } catch (ClassNotFoundException e) {
                log.error("Ошибка десериализации сообщения: {}", e.getMessage());
                notifyError("Ошибка формата сообщения");
            } catch (Exception e) {
                log.error("Неожиданная ошибка при чтении сообщения: {}", e.getMessage());
                notifyError("Неожиданная ошибка");
                disconnect();
            }
        });
    }

    private void handleReceivedMessage(ChatMessageDTO message) {
        if (message.isImage()) {
            notifyImageReceived(message);
            log.info("Получено изображение: {} ({} bytes)",
                    message.getFileName(), message.getFileData().length);
        } else if (message.isText()) {
            notifyMessageReceived(message);
            log.debug("Получено сообщение: {}", message.getContent());
        }
    }

    @Override
    public void sendMessage(String message) {
        sendChatMessage(ChatMessageDTO.builder()
                .content(message)
                .type(ChatMessageDTO.MessageType.TEXT)
                .timestamp(LocalDateTime.now())
                .sender(getLocalPeerName())
                .encrypted(true)
                .build());
    }

    @Override
    public void sendImage(byte[] imageData, String fileName) {
        sendChatMessage(ChatMessageDTO.builder()
                .type(ChatMessageDTO.MessageType.IMAGE)
                .fileName(fileName)
                .fileData(imageData)
                .fileSize(imageData.length)
                .timestamp(LocalDateTime.now())
                .sender(getLocalPeerName())
                .encrypted(true)
                .build());
    }

    @Override
    public void sendFile(byte[] fileData, String fileName) {
        sendChatMessage(ChatMessageDTO.builder()
                .type(ChatMessageDTO.MessageType.FILE)
                .fileName(fileName)
                .fileData(fileData)
                .fileSize(fileData.length)
                .timestamp(LocalDateTime.now())
                .sender(getLocalPeerName())
                .encrypted(true)
                .build());
    }

    private void sendChatMessage(ChatMessageDTO message) {
        if (!connected || outputStream == null) {
            notifyError("Соединение не установлено");
            return;
        }

        try {
            // Подготавливаем сообщение для передачи
            ChatMessageDTO preparedMessage = encryptionUtil.encryptMessage(message, connectedPeerIp);
            outputStream.writeObject(preparedMessage);
            outputStream.flush();

            log.debug("Сообщение отправлено: {}", message.getType());

        } catch (IOException e) {
            log.error("Ошибка отправки сообщения: {}", e.getMessage());
            notifyError("Ошибка отправки сообщения");
            disconnect();
        } catch (Exception e) {
            log.error("Неожиданная ошибка при отправке сообщения: {}", e.getMessage());
            notifyError("Неожиданная ошибка при отправке");
            disconnect();
        }
    }

    @Override
    public boolean isConnected() {
        return connected && connectedSocket != null && !connectedSocket.isClosed();
    }

    @Override
    public String getConnectedPeer() {
        return connectedPeerIp;
    }

    @Override
    public void disconnect() {
        connected = false;
        listening = false;

        try {
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
            if (connectedSocket != null) {
                connectedSocket.close();
                connectedSocket = null;
            }
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (IOException e) {
            log.error("Ошибка при закрытии соединения: {}", e.getMessage());
        }

        connectedPeerIp = null;
        notifyConnectionStatusChanged(false, "Отключено");
        log.info("P2P чат отключен");
    }

    @Override
    public void stopListening() {
        listening = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            log.error("Ошибка остановки прослушивания: {}", e.getMessage());
        }
    }

    // Методы уведомления слушателей
    private void notifyMessageReceived(ChatMessageDTO message) {
        for (ChatListener listener : listeners) {
            listener.onMessageReceived(message);
        }
    }

    private void notifyImageReceived(ChatMessageDTO imageMessage) {
        for (ChatListener listener : listeners) {
            listener.onImageReceived(imageMessage);
        }
    }

    private void notifyConnectionStatusChanged(boolean connected, String peerInfo) {
        for (ChatListener listener : listeners) {
            listener.onConnectionStatusChanged(connected, peerInfo);
        }
    }

    private void notifyError(String errorMessage) {
        for (ChatListener listener : listeners) {
            listener.onError(errorMessage);
        }
    }

    private void notifyIncomingConnection(String peerIp) {
        for (ChatListener listener : listeners) {
            listener.onIncomingConnection(peerIp);
        }
    }

    @Override
    public void addListener(ChatListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(ChatListener listener) {
        listeners.remove(listener);
    }

    private String getLocalPeerName() {
        // Можно получить имя устройства или оставить константу
        return System.getProperty("user.name", "Peer");
    }
}
