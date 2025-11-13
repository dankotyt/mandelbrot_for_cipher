package com.cipher.client.service.localNetwork;

import com.cipher.core.dto.DeviceDTO;
import com.cipher.core.dto.connection.ConnectionRequestDTO;
import com.cipher.core.service.network.NetworkService;
import com.cipher.core.service.network.impl.ConnectionCoordinatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.cipher.common.utils.NetworkConstants.CONNECTION_PORT;

/**
 * Сервер для приема запросов на подключение
 */
@Slf4j
@Component
public class ConnectionRequestServer implements Runnable {

    private final NetworkService networkService;
    private final ConnectionCoordinatorService connectionCoordinatorService;

    private ServerSocket serverSocket;
    private final ExecutorService connectionPool = Executors.newCachedThreadPool();
    private volatile boolean running = false;
    private  String localIpAddress;

    public ConnectionRequestServer(NetworkService networkService,
                                   ConnectionCoordinatorService connectionCoordinatorService) {
        this.networkService = networkService;
        this.connectionCoordinatorService = connectionCoordinatorService;
        initializeLocalIp();
    }

    private void initializeLocalIp() {
        try {
            this.localIpAddress = networkService.getLocalIpAddress();
            log.info("🔧 ConnectionRequestServer initialized with local IP: {}", localIpAddress);
        } catch (Exception e) {
            log.error("❌ CRITICAL: Failed to get local IP address: {}", e.getMessage());
            // Используем fallback
            try {
                this.localIpAddress = InetAddress.getLocalHost().getHostAddress();
                log.warn("⚠️ Using fallback local IP: {}", localIpAddress);
            } catch (Exception ex) {
                this.localIpAddress = "127.0.0.1";
                log.error("🚨 Using default local IP: {}", localIpAddress);
            }
        }
    }

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
            // ✅ ПРОСТОЙ И РАБОЧИЙ ВАРИАНТ
            this.serverSocket = new ServerSocket(CONNECTION_PORT);

            Thread serverThread = new Thread(this, "Connection-Request-Server");
            serverThread.setDaemon(true);
            serverThread.start();
            log.info("✅ ConnectionRequestServer started on port {}", CONNECTION_PORT);

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

            String[] parts = message.split(":");
            if (parts.length < 3) {
                log.warn("Invalid connection message format from {}: {}", fromIp, message);
                return;
            }

            String messageType = parts[0];
            String deviceName = parts[1];
            String deviceIpFromMessage = parts[2];

            DeviceDTO fromDevice = new DeviceDTO(deviceName, deviceIpFromMessage);

            // ✅ РАЗДЕЛЯЕМ ЛОГИКУ ДЛЯ ЗАПРОСОВ И ОТВЕТОВ
            switch (messageType) {
                case "CONNECT_REQUEST":
                    // ДЛЯ ЗАПРОСОВ - проверяем, не от себя ли
                    if (isOwnIpAddress(fromIp) || isOwnIpAddress(deviceIpFromMessage)) {
                        log.warn("🚫 Игнорируем запрос от собственного IP: {} (local: {})", fromIp, localIpAddress);
                        return;
                    }
                    handleConnectRequest(fromDevice);
                    break;

                case "ACCEPT_RESPONSE":
                case "REJECT_RESPONSE":
                    // ✅ ДЛЯ ОТВЕТОВ - разрешаем получение от любого IP (включая себя)
                    // Это нормально - получать ответы на наши запросы
                    log.debug("✅ Получен ответ {} от: {}", messageType, fromIp);
                    if ("ACCEPT_RESPONSE".equals(messageType)) {
                        handleAcceptResponse(fromDevice);
                    } else {
                        handleRejectResponse(fromDevice);
                    }
                    break;

                default:
                    log.warn("Unknown connection message type: {} from {}", messageType, fromIp);
            }
        }

        private void handleConnectRequest(DeviceDTO fromDevice) {
            log.info("📨 Received connection request from: {} ({})",
                    fromDevice.name(), fromDevice.ip());

            DeviceDTO currentDevice = networkService.getCurrentDevice();

            ConnectionRequestDTO request = new ConnectionRequestDTO(
                    fromDevice.name(),
                    fromDevice.ip(),
                    currentDevice.name(),
                    currentDevice.ip(),
                    LocalDateTime.now(),
                    ConnectionRequestDTO.RequestStatus.PENDING
            );

            connectionCoordinatorService.handleIncomingRequest(request);
        }

        private void handleAcceptResponse(DeviceDTO fromDevice) {
            log.info("✅ Connection accepted by: {} ({})", fromDevice.name(), fromDevice.ip());

            // ✅ УВЕДОМЛЯЕМ COORDINATOR SERVICE О ПРИНЯТОМ ОТВЕТЕ
            DeviceDTO currentDevice = networkService.getCurrentDevice();

            ConnectionRequestDTO response = new ConnectionRequestDTO(
                    currentDevice.name(),
                    currentDevice.ip(),
                    fromDevice.name(),
                    fromDevice.ip(),
                    LocalDateTime.now(),
                    ConnectionRequestDTO.RequestStatus.ACCEPTED
            );

            connectionCoordinatorService.handleResponseReceived(response);
        }

        private void handleRejectResponse(DeviceDTO fromDevice) {
            log.info("❌ Connection rejected by: {} ({})", fromDevice.name(), fromDevice.ip());

            // ✅ УВЕДОМЛЯЕМ COORDINATOR SERVICE ОБ ОТКЛОНЕННОМ ОТВЕТЕ
            DeviceDTO currentDevice = networkService.getCurrentDevice();

            ConnectionRequestDTO response = new ConnectionRequestDTO(
                    currentDevice.name(),
                    currentDevice.ip(),
                    fromDevice.name(),
                    fromDevice.ip(),
                    LocalDateTime.now(),
                    ConnectionRequestDTO.RequestStatus.REJECTED
            );

            connectionCoordinatorService.handleResponseReceived(response);
        }

        private boolean isOwnIpAddress(String ip) {
            if (ip == null) {
                log.warn("⚠️ Received null IP address");
                return true;
            }

            // Переинициализируем IP при необходимости
            if (localIpAddress == null) {
                initializeLocalIp();
            }

            boolean isOwn = ip.equals(localIpAddress) ||
                    ip.equals("127.0.0.1") ||
                    ip.equals("localhost");

            if (isOwn) {
                log.warn("🚫 BLOCKED: Own IP detected - Received: {}, Local: {}", ip, localIpAddress);
            } else {
                log.debug("✅ ALLOWED: External IP - Received: {}, Local: {}", ip, localIpAddress);
            }

            return isOwn;
        }
    }
}