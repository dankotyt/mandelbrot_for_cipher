package com.cipher.client.service.localNetwork;

import com.cipher.common.utils.NetworkConstants;
import com.cipher.core.dto.connection.ConnectionRequestDTO;
import com.cipher.core.dto.DeviceDTO;
import com.cipher.core.model.ECDHKeyExchange;
import com.cipher.core.service.network.*;
import com.cipher.core.service.network.impl.NetworkServiceImpl;
import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javafx.application.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.cipher.common.utils.NetworkConstants.APP_PORT;
import static com.cipher.common.utils.NetworkConstants.CONNECTION_PORT;

/**управление подключениями между устройствами**/
@Service
@Slf4j
@RequiredArgsConstructor
public class AppConnectionService {
    private static final Logger logger = LoggerFactory.getLogger(AppConnectionService.class);

    private ServerSocket appServerSocket;
    private ServerSocket keyExchangeServerSocket;
    private boolean running = false;
    private final ExecutorService connectionPool = Executors.newCachedThreadPool();

    private final ConnectionService connectionService;
    private final DialogDisplayer dialogDisplayer;
    private final NetworkServiceImpl networkServiceImpl;
    private final SenderConnectionService senderConnectionService;
    private final KeyExchangeService keyExchangeService;
    private final SceneManager sceneManager;
    private final ConnectionManager connectionManager;

    @PostConstruct
    public void startServer() {
        // ЗАПУСКАЕМ ВСЕГДА (для обработки входящих подключений)
        new Thread(this::runAppServer).start();
        new Thread(this::runKeyExchangeServer).start();

        // НЕ запускаем discovery сервер здесь!
        // Он запустится только при входе в раздел устройств

        log.info("Серверы приложения запущены (невидимы в сети)");
    }

    private void runAppServer() {
        try {
            appServerSocket = new ServerSocket(NetworkConstants.APP_PORT);
            running = true;
            log.info("Сервер приложения запущен на порту {}", NetworkConstants.APP_PORT);

            while (running) {
                Socket clientSocket = appServerSocket.accept();
                handleIncomingConnection(clientSocket);
            }
        } catch (IOException e) {
            log.error("Ошибка сервера приложения: {}", e.getMessage());
        }
    }

    private void runKeyExchangeServer() {
        try {
            keyExchangeServerSocket = new ServerSocket(NetworkConstants.KEY_EXCHANGE_PORT);
            log.info("✅ Сервер обмена ключами запущен на порту {}", NetworkConstants.KEY_EXCHANGE_PORT);

            while (running) {
                Socket clientSocket = keyExchangeServerSocket.accept();
                log.info("🔑 Принято входящее соединение для обмена ключами от: {}",
                        clientSocket.getInetAddress().getHostAddress());
                handleKeyExchangeConnection(clientSocket);
            }
        } catch (IOException e) {
            log.error("❌ Ошибка сервера обмена ключами: {}", e.getMessage());
        }
    }

    private void runServer() {
        try {
            appServerSocket = new ServerSocket(APP_PORT);
            running = true;
            log.info("Сервер приложения запущен на порту {}", APP_PORT);

            while (running) {
                Socket clientSocket = appServerSocket.accept();
                handleIncomingConnection(clientSocket);
            }
        } catch (IOException e) {
            log.error("Ошибка сервера: {}", e.getMessage());
        }
    }

    private void handleKeyExchangeConnection(Socket clientSocket) {
        connectionPool.execute(() -> {
            String clientIp = clientSocket.getInetAddress().getHostAddress();
            log.info("Входящее соединение для обмена ключами от: {}", clientIp);

            try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                // Получаем публичный ключ клиента
                int keyLength = in.readInt();
                if (keyLength <= 0 || keyLength > 10000) {
                    throw new IOException("Неверная длина ключа: " + keyLength);
                }

                byte[] clientPublicKeyBytes = new byte[keyLength];
                in.readFully(clientPublicKeyBytes);

                // Отправляем наш публичный ключ
                ECDHKeyExchange ourKeys = keyExchangeService.getCurrentKeys();
                byte[] ourPublicKey = ourKeys.getPublicKeyBytes();
                out.writeInt(ourPublicKey.length);
                out.write(ourPublicKey);
                out.flush();

                // Вычисляем общий секрет
                ourKeys.computeSharedSecret(clientPublicKeyBytes);

                // Сохраняем соединение
                keyExchangeService.addConnection(clientSocket.getInetAddress(), ourKeys);

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
                            "Принять подключение и открыть чат?",
                    "Принять",
                    "Отклонить"
            );

            if (accepted) {
                // Отправляем подтверждение
                DeviceDTO currentDevice = networkServiceImpl.getCurrentDevice();
                senderConnectionService.sendAcceptResponse(clientIp, currentDevice);

                ConnectionRequestDTO request = createRequestDTO(remoteDevice,
                        ConnectionRequestDTO.RequestStatus.ACCEPTED);
                connectionService.acceptConnectionRequest(request);

                try {
                    // УСТАНАВЛИВАЕМ ПОДКЛЮЧЕННОГО ПИРА В CONNECTION MANAGER
                    InetAddress peerAddress = InetAddress.getByName(clientIp);
                    connectionManager.setConnectedPeer(peerAddress);

                    sceneManager.showChatPanel(remoteDevice);
                    logger.info("✅ Чат автоматически открыт с: {}", clientIp);
                } catch (Exception e) {
                    logger.error("❌ Ошибка при автоматическом открытии чата: {}", e.getMessage());
                }

                // Запускаем обмен ключами в фоне
                new Thread(() -> {
                    try {
                        log.info("🔄 Инициируем обмен ключами с: {}", clientIp);
                        Thread.sleep(1000);

                        InetAddress peerAddress = InetAddress.getByName(clientIp);
                        boolean keyExchangeSuccess = keyExchangeService.performKeyExchange(peerAddress);

                        if (keyExchangeSuccess) {
                            log.info("✅ Обмен ключами успешно завершен с: {}", clientIp);
                        } else {
                            log.error("❌ Обмен ключами не удался с: {}", clientIp);
                        }
                    } catch (Exception e) {
                        log.error("❌ Ошибка при обмене ключами: {}", e.getMessage());
                    }
                }).start();

            } else {
                // Отправляем отклонение
                DeviceDTO currentDevice = networkServiceImpl.getCurrentDevice();
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
            InetAddress peerAddress = InetAddress.getByName(clientIp);

            connectionManager.setConnectedPeer(peerAddress);

            boolean keyExchangeSuccess = keyExchangeService.performKeyExchange(peerAddress);

            if (keyExchangeSuccess) {
                log.info("✅ Обмен ключами успешно завершен с: {}", clientIp);

                if (keyExchangeService.hasKeysForPeer(clientIp)) {
                    log.info("✅ Ключи успешно сохранены для: {}", clientIp);
                }

                // УВЕДОМЛЯЕМ, что подключение установлено, но НЕ открываем чат автоматически
                // Чат уже был открыт при отправке запроса
                Platform.runLater(() -> {
                    // Можно показать уведомление, что подключение установлено
                    logger.info("✅ Подключение установлено с: {}", clientIp);
                });

            } else {
                log.error("❌ Обмен ключами не удался с: {}", clientIp);
            }

        } catch (Exception e) {
            logger.error("❌ Ошибка при установке соединения: {}", e.getMessage());
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
        DeviceDTO currentDevice = networkServiceImpl.getCurrentDevice();

        return new ConnectionRequestDTO(
                currentDevice.name(), currentDevice.ip(),
                remoteDevice.name(), remoteDevice.ip(),
                LocalDateTime.now(), status
        );
    }

    @PreDestroy
    public void stopServer() {
        running = false;
        try {
            if (appServerSocket != null) {
                appServerSocket.close();
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
