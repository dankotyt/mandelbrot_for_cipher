package com.cipher.client.service.localNetwork;

import com.cipher.core.dto.DeviceDTO;
import com.cipher.core.service.network.NetworkService;
import com.cipher.core.service.network.impl.ConnectionCoordinatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.cipher.common.utils.NetworkConstants.CONNECTION_PORT;

/**
 * Сервер для приема запросов на подключение
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionRequestServer implements Runnable {

    private final NetworkService networkService;
    private final ConnectionCoordinatorService connectionCoordinatorService;

    private ServerSocket serverSocket;
    private final ExecutorService connectionPool = Executors.newCachedThreadPool();
    private volatile boolean running = false;

    @Override
    public void run() {
        running = true;
        log.info("Connection request server started on port {}", CONNECTION_PORT);

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Socket clientSocket = serverSocket.accept();
                connectionPool.execute(new RequestProcessor(clientSocket));

            } catch (IOException e) {
                if (running) {
                    log.error("Error accepting connection request: {}", e.getMessage());
                }
                break;
            }
        }
    }

    public void startServer() {
        try {
            serverSocket = new ServerSocket(CONNECTION_PORT);
            Thread serverThread = new Thread(this, "Connection-Request-Server");
            serverThread.setDaemon(true);
            serverThread.start();
            log.info("✅ Connection request server started on port {}", CONNECTION_PORT);
        } catch (IOException e) {
            log.error("❌ Failed to start connection request server: {}", e.getMessage());
        }
    }

    public void stopServer() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.error("Error stopping connection request server: {}", e.getMessage());
        }
        connectionPool.shutdown();
    }

    /**
     * Внутренний класс для обработки отдельных соединений
     */
    private class RequestProcessor implements Runnable {
        private final Socket clientSocket;
        private final String clientAddress;

        public RequestProcessor(Socket socket) {
            this.clientSocket = socket;
            this.clientAddress = socket.getInetAddress().getHostAddress();
        }

        @Override
        public void run() {
            log.info("Processing connection request from: {}", clientAddress);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()))) {

                String message = reader.readLine();
                if (message != null) {
                    processConnectionMessage(message, clientAddress);
                }

            } catch (IOException e) {
                log.error("Error processing connection request from {}: {}", clientAddress, e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    log.warn("Error closing connection request socket: {}", e.getMessage());
                }
            }
        }

        private void processConnectionMessage(String message, String fromIp) {
            log.debug("Received connection message: {} from {}", message, fromIp);

            if (isOwnIpAddress(fromIp)) {
                log.debug("Игнорируем запрос от собственного IP: {}", fromIp);
                return;
            }

            String[] parts = message.split(":");
            if (parts.length < 3) {
                log.warn("Invalid connection message format from {}: {}", fromIp, message);
                return;
            }

            String messageType = parts[0];
            String deviceName = parts[1];
            String deviceIp = parts[2];

            DeviceDTO fromDevice = new DeviceDTO(deviceName, deviceIp);

            switch (messageType) {
                case "CONNECT_REQUEST":
                    handleConnectRequest(fromDevice);
                    break;
                case "ACCEPT_RESPONSE":
                    handleAcceptResponse(fromDevice);
                    break;
                case "REJECT_RESPONSE":
                    handleRejectResponse(fromDevice);
                    break;
                default:
                    log.warn("Unknown connection message type: {} from {}", messageType, fromIp);
            }
        }

        private void handleConnectRequest(DeviceDTO fromDevice) {
            log.info("📨 Received connection request from: {} ({})",
                    fromDevice.name(), fromDevice.ip());

            DeviceDTO currentDevice = networkService.getCurrentDevice();

            com.cipher.core.dto.connection.ConnectionRequestDTO request =
                    new com.cipher.core.dto.connection.ConnectionRequestDTO(
                            fromDevice.name(),
                            fromDevice.ip(),
                            currentDevice.name(),
                            currentDevice.ip(),
                            LocalDateTime.now(),
                            com.cipher.core.dto.connection.ConnectionRequestDTO.RequestStatus.PENDING
                    );

            // ✅ Прямой вызов без циклической зависимости
            connectionCoordinatorService.handleIncomingRequest(request);
        }

        private void handleAcceptResponse(DeviceDTO fromDevice) {
            log.info(" Connection accepted by: {} ({})", fromDevice.name(), fromDevice.ip());
        }

        private void handleRejectResponse(DeviceDTO fromDevice) {
            log.info("❌ Connection rejected by: {} ({})", fromDevice.name(), fromDevice.ip());
        }

        private boolean isOwnIpAddress(String ip) {
            try {
                String localIp = networkService.getLocalIpAddress();
                return ip.equals(localIp) ||
                        ip.equals("127.0.0.1") ||
                        ip.equals("localhost") ||
                        networkService.isAppRunning(ip); // Проверяем, не наш ли это IP
            } catch (Exception e) {
                return false;
            }
        }
    }
}