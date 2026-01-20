package com.cipher.core.service.network.impl;

import com.cipher.client.service.localNetwork.SenderConnectionService;
import com.cipher.core.dto.connection.ConnectionRequestDTO;
import com.cipher.core.dto.DeviceDTO;
import com.cipher.core.service.network.ConnectionService;
import com.cipher.core.utils.DialogDisplayer;
import javafx.application.Platform;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**управляет бизнес-логикой подключений**/
@Service
@RequiredArgsConstructor
public class ConnectionServiceImpl implements ConnectionService {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionServiceImpl.class);

    private final DialogDisplayer dialogDisplayer;
    private final NetworkServiceImpl networkServiceImpl;
    private final SenderConnectionService senderConnectionService;

    private final Map<String, ConnectionRequestDTO> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, ConnectionRequestDTO> establishedConnections = new ConcurrentHashMap<>();
    private final List<ConnectionListener> listeners = new ArrayList<>();
    private final Map<String, Object> requestLocks = new ConcurrentHashMap<>();

    @Override
    public void addListener(ConnectionListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(ConnectionListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void sendConnectionRequest(DeviceDTO toDevice) {
        String lockKey = toDevice.ip();
        Object lock = requestLocks.computeIfAbsent(lockKey, k -> new Object());

        synchronized (lock) {
            try {
                if (isRequestPending(toDevice.ip())) {
                    logger.warn("Запрос к {} уже отправлен", toDevice.ip());
                    return;
                }

                DeviceDTO currentDevice = networkServiceImpl.getCurrentDevice();
                boolean sent = senderConnectionService.sendConnectionRequest(toDevice.ip(), currentDevice);

                if (sent) {
                    ConnectionRequestDTO request = new ConnectionRequestDTO(
                            currentDevice.name(), currentDevice.ip(),
                            toDevice.name(), toDevice.ip(),
                            LocalDateTime.now(),
                            ConnectionRequestDTO.RequestStatus.PENDING
                    );

                    String requestId = generateRequestId(request.getFromDeviceIpAsInetAddress(),
                            request.getToDeviceIpAsInetAddress());
                    pendingRequests.put(requestId, request);

                    notifyRequestReceived(request);
                } else {
                    logger.error("Не удалось отправить запрос к {}", toDevice.ip());
                }

            } catch (Exception e) {
                logger.error("Ошибка при отправке запроса: {}", e.getMessage(), e);
            }
        }
    }

    private boolean isRequestPending(String deviceIp) {
        return pendingRequests.values().stream()
                .anyMatch(request ->
                        request.toDeviceIp().equals(deviceIp) &&
                                request.status() == ConnectionRequestDTO.RequestStatus.PENDING);
    }

    @Override
    public boolean isConnectionEstablished(String deviceIp) {
        return establishedConnections.values().stream()
                .anyMatch(request ->
                        request.toDeviceIp().equals(deviceIp) &&
                                request.status() == ConnectionRequestDTO.RequestStatus.ACCEPTED);
    }

    @Override
    public void acceptConnectionRequest(ConnectionRequestDTO request) {
        try {
            String requestId = generateRequestId(request.getFromDeviceIpAsInetAddress(),
                    request.getToDeviceIpAsInetAddress());

            ConnectionRequestDTO updatedRequest = new ConnectionRequestDTO(
                    request.fromDeviceName(),
                    request.fromDeviceIp(),
                    request.toDeviceName(),
                    request.toDeviceIp(),
                    request.timestamp(),
                    ConnectionRequestDTO.RequestStatus.ACCEPTED
            );

            pendingRequests.remove(requestId);
            establishedConnections.put(requestId, updatedRequest); // Сохраняем установленное соединение

            logger.info("Запрос на подключение принят: {} -> {}",
                    request.fromDeviceIp(), request.toDeviceIp());

            notifyRequestAccepted(updatedRequest);

        } catch (Exception e) {
            logger.error("Ошибка при принятии запроса: {}", e.getMessage(), e);
        }
    }

    @Override
    public void rejectConnectionRequest(ConnectionRequestDTO request) {
        try {
            String requestId = generateRequestId(request.getFromDeviceIpAsInetAddress(),
                    request.getToDeviceIpAsInetAddress());

            ConnectionRequestDTO updatedRequest = new ConnectionRequestDTO(
                    request.fromDeviceName(),
                    request.fromDeviceIp(),
                    request.toDeviceName(),
                    request.toDeviceIp(),
                    request.timestamp(),
                    ConnectionRequestDTO.RequestStatus.REJECTED
            );

            pendingRequests.remove(requestId);

            logger.info("Запрос на подключение отклонен: {} -> {}",
                    request.fromDeviceIp(), request.toDeviceIp());

            notifyRequestRejected(updatedRequest);

        } catch (Exception e) {
            logger.error("Ошибка при отклонении запроса: {}", e.getMessage(), e);
        }
    }

    @Override
    public void checkIncomingRequests() {
        DeviceDTO currentDevice = networkServiceImpl.getCurrentDevice();

        pendingRequests.values().stream()
                .filter(request -> request.toDeviceIp().equals(currentDevice.ip()))
                .filter(request -> request.status() == ConnectionRequestDTO.RequestStatus.PENDING)
                .forEach(this::showIncomingRequestAlert);
    }

    private void showIncomingRequestAlert(ConnectionRequestDTO request) {
        Platform.runLater(() -> {
            boolean accepted = dialogDisplayer.showConfirmationDialog(
                    "Запрос на подключение",
                    "Входящее подключение",
                    "Получен запрос на подключение от:\n" +
                            "Устройство: " + request.fromDeviceName() + "\n" +
                            "IP: " + request.fromDeviceIp() + "\n\n" +
                            "Принять подключение?",
                    "Принять",
                    "Отклонить"
            );

            if (accepted) {
                acceptConnectionRequest(request);
            } else {
                rejectConnectionRequest(request);
            }
        });
    }

    private void notifyRequestReceived(ConnectionRequestDTO request) {
        Platform.runLater(() -> {
            for (ConnectionListener listener : listeners) {
                listener.onRequestReceived(request);
            }
        });
    }

    private void notifyRequestAccepted(ConnectionRequestDTO request) {
        Platform.runLater(() -> {
            for (ConnectionListener listener : listeners) {
                listener.onRequestAccepted(request);
            }
        });
    }

    private void notifyRequestRejected(ConnectionRequestDTO request) {
        Platform.runLater(() -> {
            for (ConnectionListener listener : listeners) {
                listener.onRequestRejected(request);
            }
        });
    }

    private String generateRequestId(InetAddress fromIp, InetAddress toIp) {
        return fromIp.getHostAddress() + "->" + toIp.getHostAddress();
    }

    private void simulateNetworkDelay(Runnable action) {
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                action.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}
