package com.cipher.core.service.network;

import com.cipher.client.service.SenderConnectionService;
import com.cipher.core.dto.ConnectionRequestDTO;
import com.cipher.core.dto.DeviceDTO;
import com.cipher.core.service.KeyExchangeService;
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
    private final NetworkService networkService;
    private final KeyExchangeService keyExchangeService;
    private final SenderConnectionService senderConnectionService;

    private final Map<String, ConnectionRequestDTO> pendingRequests = new ConcurrentHashMap<>();
    private final List<ConnectionListener> listeners = new ArrayList<>();

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
        try {
            DeviceDTO currentDevice = networkService.getCurrentDevice();

            boolean sent = senderConnectionService.sendConnectionRequest(
                    toDevice.ip(), currentDevice);

            if (sent) {
                ConnectionRequestDTO request = new ConnectionRequestDTO(
                        currentDevice.name(), currentDevice.ip(),
                        toDevice.name(), toDevice.ip(),
                        LocalDateTime.now(),
                        ConnectionRequestDTO.RequestStatus.PENDING
                );

                notifyRequestReceived(request);

            } else {
                logger.error("Не удалось отправить запрос к {}", toDevice.ip());
            }

        } catch (Exception e) {
            logger.error("Ошибка при отправке запроса: {}", e.getMessage(), e);
        }
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
        DeviceDTO currentDevice = networkService.getCurrentDevice();

        pendingRequests.values().stream()
                .filter(request -> request.toDeviceIp().equals(currentDevice.ip()))
                .filter(request -> request.status() == ConnectionRequestDTO.RequestStatus.PENDING)
                .forEach(this::showIncomingRequestAlert);
    }

    @Override
    public boolean performKeyExchange(InetAddress peerAddress) {
        try {
            logger.info("Начало реального обмена ключами DH с: {}", peerAddress.getHostAddress());

            // Выполняем реальный обмен ключами DH
            boolean success = keyExchangeService.performKeyExchange(peerAddress);

            if (success) {
                logger.info("Обмен ключами DH успешно завершен с: {}", peerAddress.getHostAddress());

                // Проверяем, что мастер-сид действительно получен
                try {
                    byte[] masterSeed = keyExchangeService.getMasterSeedFromDH(peerAddress);
                    if (masterSeed != null && masterSeed.length > 0) {
                        logger.info("Мастер-сид успешно получен, длина: {} байт", masterSeed.length);
                        return true;
                    } else {
                        logger.error("Мастер-сид не был получен после обмена ключами");
                        return false;
                    }
                } catch (Exception e) {
                    logger.error("Ошибка при получении мастер-сида: {}", e.getMessage(), e);
                    return false;
                }
            } else {
                logger.error("Обмен ключами DH не удался с: {}", peerAddress.getHostAddress());
                return false;
            }

        } catch (Exception e) {
            logger.error("Критическая ошибка при обмене ключами DH с {}: {}",
                    peerAddress.getHostAddress(), e.getMessage(), e);
            return false;
        }
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
