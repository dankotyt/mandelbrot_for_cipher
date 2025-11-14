package com.cipher.client.service.chat.impl;

import com.cipher.client.service.chat.ChatService;
import com.cipher.client.utils.ChatEncryptionUtil;
import com.cipher.common.dto.chat.ChatMessageDTO;
import com.cipher.common.utils.NetworkConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class P2PChatServiceImpl implements ChatService {

    private Socket connectedSocket;
    private ServerSocket serverSocket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;

    private final ExecutorService messageListener = Executors.newSingleThreadExecutor();
    private final ExecutorService connectionListener = Executors.newSingleThreadExecutor();
    private final List<ChatListener> listeners = new ArrayList<>();
    private final ChatEncryptionUtil encryptionUtil;

    private volatile boolean connected = false;
    private volatile boolean listening = false;
    private String connectedPeerIp;
    private int port;


    @Override
    public boolean connectToPeer(String peerIp) {
        try {
            if (connected) {
                disconnect();
            }

            log.info("Подключение к пиру {}:{}", peerIp, port);
            connectedSocket = new Socket(peerIp, port);
            setupStreams(connectedSocket);

            connected = true;
            connectedPeerIp = peerIp;

            startMessageListening();
            notifyConnectionStatusChanged(true, "Подключен к " + peerIp);

            log.info("Успешно подключено к пиру: {}", peerIp);
            return true;

        } catch (IOException e) {
            log.error("Ошибка подключения к {}:{} - {}", peerIp, port, e.getMessage());
            notifyError("Ошибка подключения: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void startListening(int port) {
        try {
            this.port = port;
            serverSocket = new ServerSocket(port);
            listening = true;

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

                // Принимаем подключение автоматически в P2P
                handleIncomingConnection(incomingSocket, peerIp);

            } catch (IOException e) {
                if (listening) { // Только если все еще слушаем
                    log.error("Ошибка принятия подключения: {}", e.getMessage());
                }
                break;
            }
        }
    }

    private void handleIncomingConnection(Socket socket, String peerIp) {
        try {
            if (connected) {
                log.warn("Уже подключены к пиру, отклоняем новое подключение от {}", peerIp);
                socket.close();
                return;
            }

            connectedSocket = socket;
            setupStreams(connectedSocket);

            connected = true;
            connectedPeerIp = peerIp;

            startMessageListening();
            notifyConnectionStatusChanged(true, "Подключен к " + peerIp);

            log.info("Принято входящее подключение от: {}", peerIp);

        } catch (IOException e) {
            log.error("Ошибка обработки входящего подключения: {}", e.getMessage());
            notifyError("Ошибка входящего подключения");
        }
    }

    private void setupStreams(Socket socket) throws IOException {
        outputStream = new ObjectOutputStream(socket.getOutputStream());
        inputStream = new ObjectInputStream(socket.getInputStream());
    }

    private void startMessageListening() {
        messageListener.execute(() -> {
            try {
                while (connected && !connectedSocket.isClosed()) {
                    Object receivedObject = inputStream.readObject();

                    if (receivedObject instanceof ChatMessageDTO encryptedMessage) {
                        ChatMessageDTO decryptedMessage = encryptionUtil.decryptMessage(encryptedMessage, connectedPeerIp);

                        handleReceivedMessage(decryptedMessage);
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                if (connected) {
                    log.error("Ошибка чтения сообщения: {}", e.getMessage());
                    notifyError("Ошибка соединения");
                    disconnect();
                }
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
            // Только подготавливаем сообщение для передачи (кодирование бинарных данных)
            ChatMessageDTO preparedMessage = encryptionUtil.encryptMessage(message, connectedPeerIp);
            outputStream.writeObject(preparedMessage);
            outputStream.flush();

            log.debug("Сообщение отправлено: {}", message.getType());

        } catch (IOException e) {
            log.error("Ошибка отправки сообщения: {}", e.getMessage());
            notifyError("Ошибка отправки сообщения");
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
            if (outputStream != null) outputStream.close();
            if (inputStream != null) inputStream.close();
            if (connectedSocket != null) connectedSocket.close();
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {
            log.error("Ошибка при закрытии соединения: {}", e.getMessage());
        }

        messageListener.shutdownNow();
        connectionListener.shutdownNow();
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
