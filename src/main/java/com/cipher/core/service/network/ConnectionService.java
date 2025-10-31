package com.cipher.core.service.network;

import com.cipher.core.dto.ConnectionRequestDto;
import com.cipher.core.dto.DeviceDTO;
import com.cipher.core.utils.DialogDisplayer;
import javafx.application.Platform;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ConnectionService {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionService.class);

    private final DialogDisplayer dialogDisplayer;
    private final NetworkService networkService;

    private final Map<String, ConnectionRequestDto> pendingRequests = new ConcurrentHashMap<>();
    private final List<ConnectionListener> listeners = new ArrayList<>();

    public interface ConnectionListener {
        void onRequestReceived(ConnectionRequestDto request);
        void onRequestAccepted(ConnectionRequestDto request);
        void onRequestRejected(ConnectionRequestDto request);
    }

    public void addListener(ConnectionListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ConnectionListener listener) {
        listeners.remove(listener);
    }

    public void sendConnectionRequest(DeviceDTO toDevice) {
        try {
            DeviceDTO currentDevice = networkService.getCurrentDevice();

            ConnectionRequestDto request = new ConnectionRequestDto(
                    currentDevice.name(),
                    currentDevice.ip(),
                    toDevice.name(),
                    toDevice.ip(),
                    LocalDateTime.now(),
                    ConnectionRequestDto.RequestStatus.PENDING
            );

            String requestId = generateRequestId(currentDevice.ip(), toDevice.ip());
            pendingRequests.put(requestId, request);

            logger.info("Запрос на подключение отправлен от {} к {}",
                    currentDevice.ip(), toDevice.ip());

            simulateNetworkDelay(() -> notifyRequestReceived(request));

        } catch (Exception e) {
            logger.error("Ошибка при отправке запроса: {}", e.getMessage(), e);
            Platform.runLater(() ->
                    dialogDisplayer.showErrorDialog("Ошибка отправки запроса: " + e.getMessage())
            );
        }
    }

    public void acceptConnectionRequest(ConnectionRequestDto request) {
        try {
            String requestId = generateRequestId(request.fromDeviceIp(), request.toDeviceIp());

            ConnectionRequestDto updatedRequest = new ConnectionRequestDto(
                    request.fromDeviceName(),
                    request.fromDeviceIp(),
                    request.toDeviceName(),
                    request.toDeviceIp(),
                    request.timestamp(),
                    ConnectionRequestDto.RequestStatus.ACCEPTED
            );

            pendingRequests.remove(requestId);

            logger.info("Запрос на подключение принят: {} -> {}",
                    request.fromDeviceIp(), request.toDeviceIp());

            notifyRequestAccepted(updatedRequest);

        } catch (Exception e) {
            logger.error("Ошибка при принятии запроса: {}", e.getMessage(), e);
        }
    }

    public void rejectConnectionRequest(ConnectionRequestDto request) {
        try {
            String requestId = generateRequestId(request.fromDeviceIp(), request.toDeviceIp());

            ConnectionRequestDto updatedRequest = new ConnectionRequestDto(
                    request.fromDeviceName(),
                    request.fromDeviceIp(),
                    request.toDeviceName(),
                    request.toDeviceIp(),
                    request.timestamp(),
                    ConnectionRequestDto.RequestStatus.REJECTED
            );

            pendingRequests.remove(requestId);

            logger.info("Запрос на подключение отклонен: {} -> {}",
                    request.fromDeviceIp(), request.toDeviceIp());

            notifyRequestRejected(updatedRequest);

        } catch (Exception e) {
            logger.error("Ошибка при отклонении запроса: {}", e.getMessage(), e);
        }
    }

    // Метод для проверки входящих запросов для текущего устройства
    public void checkIncomingRequests() {
        DeviceDTO currentDevice = networkService.getCurrentDevice();

        pendingRequests.values().stream()
                .filter(request -> request.toDeviceIp().equals(currentDevice.ip()))
                .filter(request -> request.status() == ConnectionRequestDto.RequestStatus.PENDING)
                .forEach(this::showIncomingRequestAlert);
    }

    private void showIncomingRequestAlert(ConnectionRequestDto request) {
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

    private void notifyRequestReceived(ConnectionRequestDto request) {
        Platform.runLater(() -> {
            for (ConnectionListener listener : listeners) {
                listener.onRequestReceived(request);
            }
        });
    }

    private void notifyRequestAccepted(ConnectionRequestDto request) {
        Platform.runLater(() -> {
            for (ConnectionListener listener : listeners) {
                listener.onRequestAccepted(request);
            }
        });
    }

    private void notifyRequestRejected(ConnectionRequestDto request) {
        Platform.runLater(() -> {
            for (ConnectionListener listener : listeners) {
                listener.onRequestRejected(request);
            }
        });
    }

    private String generateRequestId(String fromIp, String toIp) {
        return fromIp + "->" + toIp;
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
