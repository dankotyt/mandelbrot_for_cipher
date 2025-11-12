package com.cipher.core.service.network.impl;

import com.cipher.client.service.localNetwork.SenderConnectionService;
import com.cipher.core.dto.connection.ConnectionRequestDTO;
import com.cipher.core.dto.DeviceDTO;
import com.cipher.core.service.network.ConnectionService;
import com.cipher.core.service.network.KeyExchangeService;
import com.cipher.core.service.network.NetworkService;
import com.cipher.core.utils.DialogDisplayer;
import javafx.application.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ConnectionCoordinatorService - "Координатор подключений"
 * Что делает: Координирует процесс подключения между устройствами
 * Только бизнес-логика подключений, без сетевых деталей
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionCoordinatorService implements ConnectionService {

    private final DialogDisplayer dialogDisplayer;
    private final NetworkService networkService;
    private final KeyExchangeService keyExchangeService;
    private final SenderConnectionService senderConnectionService;

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
                ConnectionRequestDTO request = createRequestDTO(
                        toDevice, ConnectionRequestDTO.RequestStatus.PENDING);
                notifyRequestReceived(request);
                log.info("📨 Запрос на подключение отправлен к: {}", toDevice.ip());
            } else {
                log.error("❌ Не удалось отправить запрос к {}", toDevice.ip());
                notifyError("Не удалось отправить запрос подключения");
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при отправке запроса: {}", e.getMessage(), e);
            notifyError("Ошибка отправки запроса: " + e.getMessage());
        }
    }

    @Override
    public void acceptConnectionRequest(ConnectionRequestDTO request) {
        try {
            log.info("✅ Принят запрос на подключение от: {}", request.fromDeviceIp());

            // 1. Уведомляем UI
            ConnectionRequestDTO acceptedRequest = createRequestDTO(
                    new DeviceDTO(request.fromDeviceName(), request.fromDeviceIp()),
                    ConnectionRequestDTO.RequestStatus.ACCEPTED
            );
            notifyRequestAccepted(acceptedRequest);

            // 2. Отправляем подтверждение
            DeviceDTO currentDevice = networkService.getCurrentDevice();
            senderConnectionService.sendAcceptResponse(request.fromDeviceIp(), currentDevice);

            // 3. Запускаем обмен ключами (делегируем специализированному сервису)
            InetAddress peerAddress = InetAddress.getByName(request.fromDeviceIp());
            boolean keySuccess = keyExchangeService.performKeyExchange(peerAddress);

            if (keySuccess) {
                log.info("🔑 Обмен ключами успешен с: {}", request.fromDeviceIp());
            } else {
                log.error("❌ Обмен ключами не удался с: {}", request.fromDeviceIp());
                notifyError("Не удалось установить безопасное соединение");
            }

        } catch (Exception e) {
            log.error("❌ Ошибка при принятии запроса: {}", e.getMessage(), e);
            notifyError("Ошибка принятия запроса: " + e.getMessage());
        }
    }

    @Override
    public void rejectConnectionRequest(ConnectionRequestDTO request) {
        try {
            log.info("❌ Отклонен запрос на подключение от: {}", request.fromDeviceIp());

            // 1. Уведомляем UI
            ConnectionRequestDTO rejectedRequest = createRequestDTO(
                    new DeviceDTO(request.fromDeviceName(), request.fromDeviceIp()),
                    ConnectionRequestDTO.RequestStatus.REJECTED
            );
            notifyRequestRejected(rejectedRequest);

            // 2. Отправляем отклонение
            DeviceDTO currentDevice = networkService.getCurrentDevice();
            senderConnectionService.sendRejectResponse(request.fromDeviceIp(), currentDevice);

        } catch (Exception e) {
            log.error("❌ Ошибка при отклонении запроса: {}", e.getMessage(), e);
            notifyError("Ошибка отклонения запроса: " + e.getMessage());
        }
    }

    @Override
    public boolean performKeyExchange(InetAddress peerAddress) {
        return keyExchangeService.performKeyExchange(peerAddress);
    }

    @Override
    public void checkIncomingRequests() {
        // В этой архитекции входящие запросы обрабатываются автоматически
        // через NetworkBootstrap -> KeyExchangeServer -> ConnectionCoordinatorService
        log.debug("Проверка входящих запросов...");
    }

    // Вспомогательные методы
    private ConnectionRequestDTO createRequestDTO(DeviceDTO remoteDevice,
                                                  ConnectionRequestDTO.RequestStatus status) {
        DeviceDTO currentDevice = networkService.getCurrentDevice();
        return new ConnectionRequestDTO(
                currentDevice.name(), currentDevice.ip(),
                remoteDevice.name(), remoteDevice.ip(),
                LocalDateTime.now(), status
        );
    }

    // Методы уведомления слушателей
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

    private void notifyError(String errorMessage) {
        Platform.runLater(() -> {
            for (ConnectionListener listener : listeners) {
                listener.onError(errorMessage);
            }
        });
    }
}
