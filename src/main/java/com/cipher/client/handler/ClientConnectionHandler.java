package com.cipher.client.handler;

import com.cipher.client.utils.NetworkConstants;
import com.cipher.core.model.ECDHKeyExchange;
import com.cipher.core.service.network.KeyExchangeService;
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
    private final KeyExchangeService keyExchangeService;
    private final InetAddress clientAddress;

    public ClientConnectionHandler(Socket clientSocket, KeyExchangeService keyExchangeService) {
        if (clientSocket == null) {
            throw new IllegalArgumentException("Client socket cannot be null");
        }
        this.clientSocket = clientSocket;
        this.keyExchangeService = keyExchangeService;
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
        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

            clientSocket.setSoTimeout(30000);

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

        int keyLength = in.readInt();
        if (keyLength <= 0 || keyLength > 10000) {
            throw new IOException("Invalid key length: " + keyLength);
        }

        byte[] clientPublicKeyBytes = new byte[keyLength];
        in.readFully(clientPublicKeyBytes);

        ECDHKeyExchange ourKeys = keyExchangeService.getCurrentKeys();
        if (ourKeys == null) {
            sendErrorResponse(out, "No keys available");
            return;
        }

        byte[] ourPublicKey = ourKeys.getPublicKeyBytes();
        out.writeInt(ourPublicKey.length);
        out.write(ourPublicKey);
        out.flush();

        ourKeys.computeSharedSecret(clientPublicKeyBytes);

        keyExchangeService.addConnection(clientAddress, ourKeys);

        log.info("Key exchange completed with {}", clientAddress.getHostAddress());
    }

    private void handleKeyInvalidation() {
        log.info("Received key invalidation from {}", clientAddress.getHostAddress());
        keyExchangeService.generateNewKeys();
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
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            log.warn("Error closing socket for {}: {}", clientAddress.getHostAddress(), e.getMessage());
        }
    }
}