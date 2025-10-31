package com.cipher.server.handler;

import com.cipher.common.NetworkConstants;
import com.cipher.core.model.DHKeyExchange;
import com.cipher.core.service.impl.NetworkKeyExchangeServiceImpl;
import lombok.extern.slf4j.Slf4j;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

@Slf4j
public class ClientConnectionHandler implements Runnable {

    private final Socket clientSocket;
    private final InetAddress clientAddress;
    private final NetworkKeyExchangeServiceImpl networkKeyExchangeService;

    public ClientConnectionHandler(Socket clientSocket, NetworkKeyExchangeServiceImpl networkKeyExchangeService) {
        this.clientSocket = clientSocket;
        this.clientAddress = clientSocket.getInetAddress();
        this.networkKeyExchangeService = networkKeyExchangeService;
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
        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

            clientSocket.setSoTimeout(30000);

            // Читаем тип сообщения
            String messageType = in.readUTF();

            switch (messageType) {
                case NetworkConstants.KEY_EXCHANGE_MESSAGE:
                    handleKeyExchange(in, out);
                    break;
                case NetworkConstants.KEY_INVALIDATION_MESSAGE:
                    handleKeyInvalidation();
                    break;
                default:
                    log.warn("Unknown message type from {}: {}", clientAddress.getHostAddress(), messageType);
                    sendErrorResponse(out, "Unknown message type");
            }

        } catch (SocketTimeoutException e) {
            log.error("Connection timeout with {}: {}", clientAddress.getHostAddress(), e.getMessage());
        } catch (IOException e) {
            log.error("I/O error with {}: {}", clientAddress.getHostAddress(), e.getMessage());
        }
    }

    private void handleKeyExchange(DataInputStream in, DataOutputStream out) throws IOException {
        log.info("Processing key exchange request from {}", clientAddress.getHostAddress());

        // Получаем публичный ключ клиента
        int keyLength = in.readInt();
        if (keyLength <= 0 || keyLength > 10000) {
            throw new IOException("Invalid key length: " + keyLength);
        }

        byte[] clientPublicKeyBytes = new byte[keyLength];
        in.readFully(clientPublicKeyBytes);

        // Отправляем наш публичный ключ
        DHKeyExchange ourKeys = networkKeyExchangeService.getCurrentKeys();
        if (ourKeys == null) {
            sendErrorResponse(out, "No keys available");
            return;
        }

        byte[] ourPublicKey = ourKeys.getPublicKeyBytes();
        out.writeInt(ourPublicKey.length);
        out.write(ourPublicKey);
        out.flush();

        // Вычисляем общий секрет
        ourKeys.computeSharedSecret(DHKeyExchange.publicKeyFromBytes(clientPublicKeyBytes));

        // Уведомляем сервис о новом соединении
        networkKeyExchangeService.processIncomingKeyExchange(clientAddress, clientPublicKeyBytes);

        log.info("Key exchange completed with {}", clientAddress.getHostAddress());
    }

    private void handleKeyInvalidation() {
        log.info("Received key invalidation from {}", clientAddress.getHostAddress());
        networkKeyExchangeService.generateNewKeys();
    }

    private void sendErrorResponse(DataOutputStream out, String errorMessage) {
        try {
            out.writeUTF("ERROR:" + errorMessage);
            out.flush();
        } catch (IOException e) {
            log.warn("Failed to send error response to {}: {}", clientAddress.getHostAddress(), e.getMessage());
        }
    }

    private void closeSocket() {
        try {
            if (!clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            log.warn("Error closing socket for {}: {}", clientAddress.getHostAddress(), e.getMessage());
        }
    }
}
