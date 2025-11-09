package com.cipher.server.service;

import com.cipher.client.PeerConnector;
import com.cipher.client.service.SenderConnectionService;
import com.cipher.common.NetworkConstants;
import com.cipher.core.controller.network.DevicesController;
import com.cipher.core.dto.ConnectionRequestDTO;
import com.cipher.core.dto.DeviceDTO;
import com.cipher.core.service.network.ConnectionService;
import com.cipher.core.service.network.NetworkService;
import com.cipher.core.utils.DialogDisplayer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javafx.application.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;

import static com.cipher.common.NetworkConstants.APP_PORT;
import static com.cipher.common.NetworkConstants.CONNECTION_PORT;

/**управление подключениями между устройствами**/
@Service
@Slf4j
@RequiredArgsConstructor
public class AppConnectionService {
    private static final Logger logger = LoggerFactory.getLogger(AppConnectionService.class);

    private ServerSocket serverSocket;
    private boolean running = false;

    private final ConnectionService connectionService;
    private final DialogDisplayer dialogDisplayer;
    private final NetworkService networkService;
    private final SenderConnectionService senderConnectionService;
    private final PeerConnector peerConnector;

    @PostConstruct
    public void startServer() {
        new Thread(this::runServer).start();
    }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(APP_PORT);
            running = true;
            log.info("Сервер приложения запущен на порту {}", APP_PORT);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                handleIncomingConnection(clientSocket);
            }
        } catch (IOException e) {
            log.error("Ошибка сервера: {}", e.getMessage());
        }
    }

    private void handleIncomingConnection(Socket clientSocket) {
        new Thread(() -> {
            try {
                String clientIp = clientSocket.getInetAddress().getHostAddress();
                InputStream input = clientSocket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));

                String message = reader.readLine();
                log.info("Получено сообщение от {}: {}", clientIp, message);

                if (message != null) {
                    handleMessage(message, clientIp);
                }

                clientSocket.close();
            } catch (IOException e) {
                log.error("Ошибка обработки соединения: {}", e.getMessage());
            }
        }).start();
    }

    private void handleMessage(String message, String clientIp) {
        String[] parts = message.split(":");
        String messageType = parts[0];

        switch (messageType) {
            case "CONNECT_REQUEST":
                handleConnectRequest(parts, clientIp);
                break;
            case "ACCEPT_RESPONSE":
                handleAcceptResponse(parts, clientIp);
                break;
            case "REJECT_RESPONSE":
                handleRejectResponse(parts, clientIp);
                break;
            default:
                log.warn("Неизвестный тип сообщения: {}", messageType);
        }
    }

    private void handleConnectRequest(String[] parts, String clientIp) {
        String deviceName = parts[1];
        String deviceIp = parts[2];

        DeviceDTO remoteDevice = new DeviceDTO(deviceName, deviceIp);

        Platform.runLater(() -> {
            boolean accepted = dialogDisplayer.showConfirmationDialog(
                    "Запрос на подключение",
                    "Входящее подключение",
                    "Получен запрос на подключение от:\n" +
                            "Устройство: " + remoteDevice.name() + "\n" +
                            "IP: " + remoteDevice.ip() + "\n\n" +
                            "Принять подключение?",
                    "Принять",
                    "Отклонить"
            );

            if (accepted) {
                // Отправляем подтверждение
                DeviceDTO currentDevice = networkService.getCurrentDevice();
                senderConnectionService.sendAcceptResponse(clientIp, currentDevice);

                ConnectionRequestDTO request = createRequestDTO(remoteDevice,
                        ConnectionRequestDTO.RequestStatus.ACCEPTED);
                connectionService.acceptConnectionRequest(request);

            } else {
                // Отправляем отклонение
                DeviceDTO currentDevice = networkService.getCurrentDevice();
                senderConnectionService.sendRejectResponse(clientIp, currentDevice);

                ConnectionRequestDTO request = createRequestDTO(remoteDevice,
                        ConnectionRequestDTO.RequestStatus.REJECTED);
                connectionService.rejectConnectionRequest(request);
            }
        });
    }

    private void handleAcceptResponse(String[] parts, String clientIp) {
        String deviceName = parts[1];
        String deviceIp = parts[2];

        DeviceDTO remoteDevice = new DeviceDTO(deviceName, deviceIp);
        ConnectionRequestDTO request = createRequestDTO(remoteDevice,
                ConnectionRequestDTO.RequestStatus.ACCEPTED);

        connectionService.acceptConnectionRequest(request);

        try {
            // TODO: Запуск обмена ключами
            peerConnector.connectToPeer(InetAddress.getByName(clientIp));
            logger.info("Запуск обмена ключами с: {}", clientIp);
        } catch (Exception e) {
            logger.error("Ошибка при обмене ключами: {}", e.getMessage());
        }
    }

    private void handleRejectResponse(String[] parts, String clientIp) {
        String deviceName = parts[1];
        String deviceIp = parts[2];

        DeviceDTO remoteDevice = new DeviceDTO(deviceName, deviceIp);
        ConnectionRequestDTO request = createRequestDTO(remoteDevice,
                ConnectionRequestDTO.RequestStatus.REJECTED);

        connectionService.rejectConnectionRequest(request);
    }

    private ConnectionRequestDTO createRequestDTO(DeviceDTO remoteDevice,
                                                  ConnectionRequestDTO.RequestStatus status) {
        DeviceDTO currentDevice = networkService.getCurrentDevice();

        return new ConnectionRequestDTO(
                remoteDevice.name(), remoteDevice.ip(),
                currentDevice.name(), currentDevice.ip(),
                LocalDateTime.now(), status
        );
    }

    @PreDestroy
    public void stopServer() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.error("Ошибка при остановке сервера: {}", e.getMessage());
        }
    }

    public boolean isServerRunning() {
        try {
            return isPortOpen("localhost", CONNECTION_PORT, 1000);
        } catch (Exception e) {
            log.error("❌ Сервер не запущен: {}", e.getMessage());
            return false;
        }
    }

    private boolean isPortOpen(String ip, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeout);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
