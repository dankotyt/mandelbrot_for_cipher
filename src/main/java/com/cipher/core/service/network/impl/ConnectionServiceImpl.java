package com.cipher.core.service.network.impl;

import com.cipher.client.service.localNetwork.SenderConnectionService;
import com.cipher.client.utils.NetworkConstants;
import com.cipher.core.dto.connection.ConnectionRequestDTO;
import com.cipher.core.dto.DeviceDTO;
import com.cipher.core.service.network.ConnectionService;
import com.cipher.core.service.network.KeyExchangeService;
import com.cipher.core.service.network.NetworkService;
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
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для управления подключениями между устройствами
 * Обрабатывает входящие запросы подключения и управляет состоянием соединений
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ConnectionServiceImpl implements ConnectionService {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionServiceImpl.class);

    private final DialogDisplayer dialogDisplayer;
    private final NetworkService networkService;
    private final SenderConnectionService senderConnectionService;
    private final KeyExchangeService keyExchangeService;
    private final SceneManager sceneManager;

    private ServerSocket appServerSocket;
    private boolean serverRunning = false;

    private final Map<String, ConnectionRequestDTO> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, ConnectionRequestDTO> establishedConnections = new ConcurrentHashMap<>();
    private final List<ConnectionListener> listeners = new ArrayList<>();
    private final Map<String, Object> requestLocks = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        startAppServer();
    }

    @PreDestroy
    public void cleanup() {
        serverRunning = false;
        if (appServerSocket != null) {
            try {
                appServerSocket.close();
            } catch (IOException e) {
                logger.error("Ошибка при остановке сервера: {}", e.getMessage());
            }
        }
    }

    // ==================== Серверная часть ====================

    /**
     * Запускает сервер приложения для обработки входящих подключений
     */
    private void startAppServer() {
        new Thread(() -> {
            try {
                appServerSocket = new ServerSocket(NetworkConstants.APP_PORT);
                serverRunning = true;
                logger.info("Сервер приложения запущен на порту {}", NetworkConstants.APP_PORT);

                while (serverRunning) {
                    Socket clientSocket = appServerSocket.accept();
                    processIncomingConnection(clientSocket);
                }
            } catch (IOException e) {
                if (serverRunning) {
                    logger.error("Ошибка сервера приложения: {}", e.getMessage());
                }
            }
        }, "App-Server").start();
    }

    /**
     * Обрабатывает входящее соединение
     */
    private void processIncomingConnection(Socket clientSocket) {
        new Thread(() -> {
            try {
                String clientIp = clientSocket.getInetAddress().getHostAddress();
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String message = reader.readLine();

                if (message != null) {
                    logger.info("Получено сообщение от {}: {}", clientIp, message);
                    parseAndHandleMessage(message, clientIp);
                }

                clientSocket.close();
            } catch (IOException e) {
                logger.error("Ошибка обработки соединения: {}", e.getMessage());
            }
        }).start();
    }

    /**
     * Парсит и обрабатывает входящее сообщение
     */
    private void parseAndHandleMessage(String message, String clientIp) {
        String[] parts = message.split(":", 3);
        if (parts.length < 3) {
            logger.warn("Некорректный формат сообщения от {}: {}", clientIp, message);
            return;
        }

        String messageType = parts[0];
        String deviceName = parts[1];
        String deviceIp = parts[2];
        DeviceDTO remoteDevice = new DeviceDTO(deviceName, deviceIp);

        switch (messageType) {
            case "CONNECT_REQUEST":
                processIncomingRequest(createConnectionRequest(remoteDevice, ConnectionRequestDTO.RequestStatus.PENDING));
                break;
            case "ACCEPT_RESPONSE":
                processIncomingAccept(remoteDevice, clientIp);
                break;
            case "REJECT_RESPONSE":
                processIncomingReject(remoteDevice, clientIp);
                break;
            default:
                logger.warn("Неизвестный тип сообщения: {}", messageType);
        }
    }

    @Override
    public void processIncomingRequest(ConnectionRequestDTO request) {
        // Показываем диалог пользователю
        Platform.runLater(() -> {
            boolean accepted = dialogDisplayer.showConfirmationDialog(
                    "Запрос на подключение",
                    "Входящее подключение",
                    String.format("Получен запрос на подключение от:%nУстройство: %s%nIP: %s%n%nПринять подключение и открыть чат?",
                            request.fromDeviceName(), request.fromDeviceIp()),
                    "Принять",
                    "Отклонить"
            );

            if (accepted) {
                // Отправляем подтверждение
                DeviceDTO currentDevice = networkService.getCurrentDevice();
                senderConnectionService.sendAcceptResponse(request.fromDeviceIp(), currentDevice);

                // Принимаем соединение
                acceptConnection(request);

                // Запускаем обмен ключами и открываем чат
                initiateKeyExchangeAndOpenChat(request);
            } else {
                // Отправляем отклонение
                DeviceDTO currentDevice = networkService.getCurrentDevice();
                senderConnectionService.sendRejectResponse(request.fromDeviceIp(), currentDevice);

                // Отклоняем соединение
                rejectConnection(request);
            }
        });
    }

    @Override
    public void processIncomingAccept(DeviceDTO remoteDevice, String clientIp) {
        // Создаем запрос со статусом ACCEPTED
        ConnectionRequestDTO request = createConnectionRequest(remoteDevice, ConnectionRequestDTO.RequestStatus.ACCEPTED);

        // Обновляем статус соединения
        acceptConnection(request);

        // Запускаем обмен ключами
        initiateBackgroundKeyExchange(clientIp);
    }

    @Override
    public void processIncomingReject(DeviceDTO remoteDevice, String clientIp) {
        // Создаем запрос со статусом REJECTED
        ConnectionRequestDTO request = createConnectionRequest(remoteDevice, ConnectionRequestDTO.RequestStatus.REJECTED);

        // Обновляем статус соединения
        rejectConnection(request);

        // Показываем уведомление пользователю
        Platform.runLater(() ->
                dialogDisplayer.showErrorDialog(
                        String.format("Устройство %s отклонило ваш запрос на подключение", remoteDevice.name())));
    }

    // ==================== Клиентская часть ====================

    @Override
    public void initiateConnection(DeviceDTO toDevice) {
        String lockKey = toDevice.ip();
        Object lock = requestLocks.computeIfAbsent(lockKey, k -> new Object());

        synchronized (lock) {
            try {
                if (isConnectionPending(toDevice.ip())) {
                    logger.warn("Запрос к {} уже отправлен", toDevice.ip());
                    return;
                }

                // Отправляем запрос через сеть
                DeviceDTO currentDevice = networkService.getCurrentDevice();
                boolean sent = senderConnectionService.sendConnectionRequest(toDevice.ip(), currentDevice);

                if (sent) {
                    // Создаем и сохраняем запрос
                    ConnectionRequestDTO request = createConnectionRequest(toDevice, ConnectionRequestDTO.RequestStatus.PENDING);
                    String requestId = generateRequestId(request);
                    pendingRequests.put(requestId, request);

                    // Уведомляем слушателей
                    notifyRequestReceived(request);
                } else {
                    logger.error("Не удалось отправить запрос к {}", toDevice.ip());
                }

            } catch (Exception e) {
                logger.error("Ошибка при отправке запроса: {}", e.getMessage(), e);
            }
        }
    }

    // ==================== Управление статусом соединений ====================

    @Override
    public void acceptConnection(ConnectionRequestDTO request) {
        try {
            String requestId = generateRequestId(request);

            // Обновляем статус запроса
            ConnectionRequestDTO acceptedRequest = new ConnectionRequestDTO(
                    request.fromDeviceName(),
                    request.fromDeviceIp(),
                    request.toDeviceName(),
                    request.toDeviceIp(),
                    LocalDateTime.now(),
                    ConnectionRequestDTO.RequestStatus.ACCEPTED
            );

            // Перемещаем из pending в established
            pendingRequests.remove(requestId);
            establishedConnections.put(requestId, acceptedRequest);

            logger.info("Подключение принято: {} -> {}",
                    request.fromDeviceIp(), request.toDeviceIp());

            // Уведомляем слушателей
            notifyConnectionAccepted(acceptedRequest);
            notifyConnectionEstablished(acceptedRequest);

        } catch (Exception e) {
            logger.error("Ошибка при принятии соединения: {}", e.getMessage(), e);
        }
    }

    @Override
    public void rejectConnection(ConnectionRequestDTO request) {
        try {
            String requestId = generateRequestId(request);

            // Обновляем статус запроса
            ConnectionRequestDTO rejectedRequest = new ConnectionRequestDTO(
                    request.fromDeviceName(),
                    request.fromDeviceIp(),
                    request.toDeviceName(),
                    request.toDeviceIp(),
                    request.timestamp(),
                    ConnectionRequestDTO.RequestStatus.REJECTED
            );

            // Удаляем из pending
            pendingRequests.remove(requestId);

            logger.info("Подключение отклонено: {} -> {}",
                    request.fromDeviceIp(), request.toDeviceIp());

            // Уведомляем слушателей
            notifyConnectionRejected(rejectedRequest);

        } catch (Exception e) {
            logger.error("Ошибка при отклонении соединения: {}", e.getMessage(), e);
        }
    }

    @Override
    public void disconnect(String deviceIp) {
        // Находим и удаляем все соединения с этим устройством
        List<String> toRemove = new ArrayList<>();

        establishedConnections.forEach((id, request) -> {
            if (request.fromDeviceIp().equals(deviceIp) || request.toDeviceIp().equals(deviceIp)) {
                toRemove.add(id);
            }
        });

        toRemove.forEach(id -> {
            establishedConnections.remove(id);
            logger.info("Соединение разорвано: {}", id);
        });

        // Уведомляем слушателей
        notifyConnectionDisconnected(deviceIp);
    }

    // ==================== Проверки статуса ====================

    @Override
    public boolean isConnectionPending(String deviceIp) {
        return pendingRequests.values().stream()
                .anyMatch(request -> request.toDeviceIp().equals(deviceIp) &&
                        request.status() == ConnectionRequestDTO.RequestStatus.PENDING);
    }

    @Override
    public boolean isConnectionEstablished(String deviceIp) {
        return establishedConnections.values().stream()
                .anyMatch(request -> (request.fromDeviceIp().equals(deviceIp) ||
                        request.toDeviceIp().equals(deviceIp)) &&
                        request.status() == ConnectionRequestDTO.RequestStatus.ACCEPTED);
    }

    @Override
    public ConnectionRequestDTO getConnectionStatus(String deviceIp) {
        // Сначала ищем в установленных соединениях
        Optional<ConnectionRequestDTO> established = establishedConnections.values().stream()
                .filter(request -> (request.fromDeviceIp().equals(deviceIp) ||
                        request.toDeviceIp().equals(deviceIp)) &&
                        request.status() == ConnectionRequestDTO.RequestStatus.ACCEPTED)
                .findFirst();

        if (established.isPresent()) {
            return established.get();
        }

        // Затем в ожидающих
        Optional<ConnectionRequestDTO> pending = pendingRequests.values().stream()
                .filter(request -> request.toDeviceIp().equals(deviceIp) &&
                        request.status() == ConnectionRequestDTO.RequestStatus.PENDING)
                .findFirst();

        return pending.orElse(null);
    }

    // ==================== Утилиты ====================

    @Override
    public ConnectionRequestDTO createConnectionRequest(DeviceDTO remoteDevice,
                                                        ConnectionRequestDTO.RequestStatus status) {
        DeviceDTO currentDevice = networkService.getCurrentDevice();

        return new ConnectionRequestDTO(
                currentDevice.name(), currentDevice.ip(),
                remoteDevice.name(), remoteDevice.ip(),
                LocalDateTime.now(), status
        );
    }

    private String generateRequestId(ConnectionRequestDTO request) {
        try {
            InetAddress fromIp = request.getFromDeviceIpAsInetAddress();
            InetAddress toIp = request.getToDeviceIpAsInetAddress();
            return fromIp.getHostAddress() + "->" + toIp.getHostAddress();
        } catch (Exception e) {
            return request.fromDeviceIp() + "->" + request.toDeviceIp();
        }
    }

    // ==================== Обмен ключами ====================

    /**
     * Инициирует обмен ключами и открывает чат после успеха
     */
    private void initiateKeyExchangeAndOpenChat(ConnectionRequestDTO request) {
        new Thread(() -> {
            try {
                logger.info("🔄 Инициируем обмен ключами с: {}", request.fromDeviceIp());
                Thread.sleep(1000);

                InetAddress peerAddress = InetAddress.getByName(request.fromDeviceIp());
                boolean keyExchangeSuccess = keyExchangeService.performKeyExchange(peerAddress);

                if (keyExchangeSuccess) {
                    logger.info("✅ Обмен ключами успешно завершен с: {}", request.fromDeviceIp());

                    Platform.runLater(() -> {
                        DeviceDTO remoteDevice = new DeviceDTO(request.fromDeviceName(), request.fromDeviceIp());
                        sceneManager.showChatPanel(remoteDevice);
                        logger.info("✅ Чат автоматически открыт с: {}", request.fromDeviceIp());
                    });
                } else {
                    logger.error("❌ Обмен ключами не удался с: {}", request.fromDeviceIp());
                    Platform.runLater(() ->
                            dialogDisplayer.showErrorDialog(
                                    "Ошибка подключения. Не удалось установить безопасное соединение"));
                }
            } catch (Exception e) {
                logger.error("❌ Ошибка при обмене ключами: {}", e.getMessage());
            }
        }).start();
    }

    /**
     * Запускает обмен ключами в фоновом режиме
     */
    private void initiateBackgroundKeyExchange(String clientIp) {
        new Thread(() -> {
            try {
                InetAddress peerAddress = InetAddress.getByName(clientIp);
                boolean keyExchangeSuccess = keyExchangeService.performKeyExchange(peerAddress);

                if (keyExchangeSuccess) {
                    logger.info("✅ Обмен ключами успешно завершен с: {}", clientIp);

                    if (keyExchangeService.hasKeysForPeer(clientIp)) {
                        logger.info("✅ Ключи успешно сохранены для: {}", clientIp);
                    }
                } else {
                    logger.error("❌ Обмен ключами не удался с: {}", clientIp);
                }
            } catch (Exception e) {
                logger.error("❌ Ошибка при установке соединения: {}", e.getMessage());
            }
        }).start();
    }

    // ==================== Уведомления слушателей ====================

    @Override
    public void addListener(ConnectionListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(ConnectionListener listener) {
        listeners.remove(listener);
    }

    private void notifyRequestReceived(ConnectionRequestDTO request) {
        Platform.runLater(() -> {
            for (ConnectionListener listener : listeners) {
                listener.onConnectionRequested(request);
            }
        });
    }

    private void notifyConnectionAccepted(ConnectionRequestDTO request) {
        Platform.runLater(() -> {
            for (ConnectionListener listener : listeners) {
                listener.onConnectionAccepted(request);
            }
        });
    }

    private void notifyConnectionRejected(ConnectionRequestDTO request) {
        Platform.runLater(() -> {
            for (ConnectionListener listener : listeners) {
                listener.onConnectionRejected(request);
            }
        });
    }

    private void notifyConnectionEstablished(ConnectionRequestDTO request) {
        Platform.runLater(() -> {
            for (ConnectionListener listener : listeners) {
                listener.onConnectionEstablished(request);
            }
        });
    }

    private void notifyConnectionDisconnected(String deviceIp) {
        Platform.runLater(() -> {
            for (ConnectionListener listener : listeners) {
                listener.onConnectionDisconnected(deviceIp);
            }
        });
    }
}