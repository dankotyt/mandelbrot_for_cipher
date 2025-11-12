package com.cipher.client.handler;

import com.cipher.common.dto.chat.ChatMessageDTO;
import com.cipher.common.utils.NetworkConstants;
import com.cipher.core.model.ECDHKeyExchange;
import com.cipher.core.service.chat.IncomingMessageHandler;
import com.cipher.core.service.network.KeyExchangeService;
import lombok.extern.slf4j.Slf4j;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

@Slf4j
public class ClientConnectionHandler implements Runnable {

    private final Socket clientSocket;
    private final KeyExchangeService keyExchangeService;
    private final InetAddress clientAddress;
    private final IncomingMessageHandler incomingMessageHandler;

    public ClientConnectionHandler(Socket clientSocket,
                                   KeyExchangeService keyExchangeService,
                                   IncomingMessageHandler incomingMessageHandler) {
        if (clientSocket == null) {
            throw new IllegalArgumentException("Client socket cannot be null");
        }
        this.clientSocket = clientSocket;
        this.keyExchangeService = keyExchangeService;
        this.incomingMessageHandler = incomingMessageHandler;
        this.clientAddress = clientSocket.getInetAddress();
    }

    @Override
    public void run() {
        log.info("Handling incoming connection from {}", clientAddress.getHostAddress());

        try {
            handleConnection();
        } catch (Exception e) {
            log.error("Error handling connection from {}: {}", clientAddress.getHostAddress(), e.getMessage());
        } finally {
            closeSocket();
        }
    }

    private void handleConnection() {
        try {
            // ✅ Сначала определяем тип соединения
            String messageType = determineConnectionType();

            switch (messageType) {
                case NetworkConstants.KEY_EXCHANGE_MESSAGE:
                    handleKeyExchangeConnection();
                    break;
                case NetworkConstants.CHAT_MESSAGE_TYPE:
                    handleChatConnection();
                    break;
                case NetworkConstants.KEY_INVALIDATION_MESSAGE:
                    handleKeyInvalidation();
                    break;
                default:
                    log.warn("⚠️ Неизвестный тип сообщения от {}: {}", clientAddress.getHostAddress(), messageType);
            }

        } catch (SocketTimeoutException e) {
            log.error("⏰ Таймаут соединения с {}: {}", clientAddress.getHostAddress(), e.getMessage());
        } catch (IOException e) {
            log.error("📡 I/O ошибка с {}: {}", clientAddress.getHostAddress(), e.getMessage());
        } catch (Exception e) {
            log.error("❌ Неожиданная ошибка с {}: {}", clientAddress.getHostAddress(), e.getMessage(), e);
        }
    }

    /**
     * Определяет тип соединения (ключи или чат)
     */
    private String determineConnectionType() throws IOException {
        // Сохраняем первоначальный таймаут
        int originalTimeout = clientSocket.getSoTimeout();

        try {
            clientSocket.setSoTimeout(5000); // 5 секунд на определение типа

            DataInputStream in = new DataInputStream(clientSocket.getInputStream());
            return in.readUTF();

        } finally {
            // Восстанавливаем таймаут
            clientSocket.setSoTimeout(originalTimeout);
        }
    }

    /**
     * Обработка соединения для обмена ключами
     */
    private void handleKeyExchangeConnection() throws IOException {
        log.info("🔑 Обработка запроса обмена ключами от {}", clientAddress.getHostAddress());

        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

            clientSocket.setSoTimeout(30000);

            int keyLength = in.readInt();
            if (keyLength <= 0 || keyLength > 10000) {
                throw new IOException("Неверная длина ключа: " + keyLength);
            }

            byte[] clientPublicKeyBytes = new byte[keyLength];
            in.readFully(clientPublicKeyBytes);

            ECDHKeyExchange ourKeys = keyExchangeService.getCurrentKeys();
            if (ourKeys == null) {
                sendErrorResponse(out, "Ключи недоступны");
                return;
            }

            byte[] ourPublicKey = ourKeys.getPublicKeyBytes();
            out.writeInt(ourPublicKey.length);
            out.write(ourPublicKey);
            out.flush();

            ourKeys.computeSharedSecret(clientPublicKeyBytes);
            keyExchangeService.addConnection(clientAddress, ourKeys);

            log.info("✅ Обмен ключами завершен с {}", clientAddress.getHostAddress());

        } catch (Exception e) {
            log.error("❌ Ошибка обмена ключами с {}: {}", clientAddress.getHostAddress(), e.getMessage());
            throw e;
        }
    }

    /**
     * ✅ НОВЫЙ МЕТОД: Обработка соединения для сообщений чата
     */
    private void handleChatConnection() {
        String peerIp = clientAddress.getHostAddress();

        log.info("💬 Начало обработки сообщений чата от: {}", peerIp);

        try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {

            while (!clientSocket.isClosed() && clientSocket.isConnected()) {
                try {
                    // Читаем входящие сообщения
                    Object receivedObject = in.readObject();

                    if (receivedObject instanceof ChatMessageDTO message) {
                        log.debug("📨 Получено сообщение от {}: {}", peerIp, message.getType());

                        // ✅ ПЕРЕДАЕМ сообщение в MessageService для обработки
                        incomingMessageHandler.handleIncomingMessage(peerIp, message);
                    }

                } catch (java.io.EOFException e) {
                    log.debug("📭 Соединение закрыто: {}", peerIp);
                    break;
                } catch (SocketException e) {
                    log.debug("🔌 Соединение разорвано: {}", peerIp);
                    break;
                } catch (ClassNotFoundException e) {
                    log.error("❌ Неизвестный тип объекта от {}: {}", peerIp, e.getMessage());
                } catch (Exception e) {
                    log.error("❌ Ошибка чтения сообщения от {}: {}", peerIp, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("❌ Ошибка обработки соединения чата с {}: {}", peerIp, e.getMessage());
        }

        log.info("🔚 Завершена обработка чата с: {}", peerIp);
    }

    private void handleKeyInvalidation() throws IOException {
        log.info("🔄 Получена инвалидация ключей от {}", clientAddress.getHostAddress());
        keyExchangeService.generateNewKeys();
    }

    private void sendErrorResponse(DataOutputStream out, String errorMessage) {
        try {
            out.writeUTF("ERROR:" + errorMessage);
            out.flush();
        } catch (IOException e) {
            log.warn("⚠️ Не удалось отправить ошибку {}: {}", clientAddress.getHostAddress(), e.getMessage());
        }
    }

    private void closeSocket() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
                log.debug("🔌 Сокет закрыт для: {}", clientAddress.getHostAddress());
            }
        } catch (IOException e) {
            log.warn("⚠️ Ошибка закрытия сокета {}: {}", clientAddress.getHostAddress(), e.getMessage());
        }
    }
}