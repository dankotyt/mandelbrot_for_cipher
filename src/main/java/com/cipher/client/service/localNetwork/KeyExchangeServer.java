package com.cipher.client.service.localNetwork;

import com.cipher.common.utils.NetworkConstants;
import com.cipher.core.model.DHKeyExchange;
import com.cipher.core.service.network.KeyExchangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeyExchangeServer {

    private final KeyExchangeService keyExchangeService;
    private ServerSocket serverSocket;
    private final ExecutorService connectionPool = Executors.newCachedThreadPool();
    private volatile boolean running = false;

    public void startServer() {
        try {
            serverSocket = new ServerSocket(NetworkConstants.KEY_EXCHANGE_PORT);
            running = true;

            log.info("Key exchange server started on port {}", NetworkConstants.KEY_EXCHANGE_PORT);

            new Thread(this::acceptConnections, "Key-Exchange-Server").start();

        } catch (IOException e) {
            log.error("Failed to start key exchange server: {}", e.getMessage());
        }
    }

    private void acceptConnections() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Socket clientSocket = serverSocket.accept();
                connectionPool.execute(new KeyExchangeHandler(clientSocket));

            } catch (IOException e) {
                if (running) {
                    log.error("Error accepting key exchange connection: {}", e.getMessage());
                }
                break;
            }
        }
    }

    private class KeyExchangeHandler implements Runnable {
        private final Socket clientSocket;
        private final InetAddress clientAddress;

        public KeyExchangeHandler(Socket socket) {
            this.clientSocket = socket;
            this.clientAddress = socket.getInetAddress();
        }

        @Override
        public void run() {
            log.info("Key exchange connection from: {}", clientAddress.getHostAddress());

            try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                // Получаем публичный ключ клиента
                int keyLength = in.readInt();
                if (keyLength <= 0 || keyLength > 10000) {
                    throw new IOException("Invalid key length: " + keyLength);
                }

                byte[] clientPublicKeyBytes = new byte[keyLength];
                in.readFully(clientPublicKeyBytes);

                // Отправляем наш публичный ключ
                DHKeyExchange ourKeys = keyExchangeService.getCurrentKeys();
                byte[] ourPublicKey = ourKeys.getPublicKeyBytes();
                out.writeInt(ourPublicKey.length);
                out.write(ourPublicKey);
                out.flush();

                // Вычисляем общий секрет
                ourKeys.computeSharedSecret(DHKeyExchange.publicKeyFromBytes(clientPublicKeyBytes));

                // Сохраняем соединение
                keyExchangeService.addConnection(clientAddress, ourKeys);

                log.info("Key exchange completed with: {}", clientAddress.getHostAddress());

            } catch (IOException e) {
                log.error("Key exchange failed with {}: {}", clientAddress.getHostAddress(), e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    log.warn("Error closing key exchange socket: {}", e.getMessage());
                }
            }
        }
    }

    public void stopServer() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.error("Error stopping key exchange server: {}", e.getMessage());
        }
        connectionPool.shutdown();
    }
}
