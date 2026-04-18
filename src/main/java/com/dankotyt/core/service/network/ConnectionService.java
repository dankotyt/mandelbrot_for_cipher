package com.dankotyt.core.service.network;

import com.dankotyt.core.dto.connection.ConnectionRequestDTO;
import com.dankotyt.core.dto.DeviceDTO;

public interface ConnectionService {

    // Управление слушателями
    void addListener(ConnectionListener listener);
    void removeListener(ConnectionListener listener);

    // Инициация подключения (клиентская сторона)
    void initiateConnection(DeviceDTO toDevice);

    // Обработка входящих запросов (серверная сторона)
    void processIncomingRequest(ConnectionRequestDTO request);
    void processIncomingAccept(DeviceDTO remoteDevice, String clientIp);
    void processIncomingReject(DeviceDTO remoteDevice, String clientIp);

    // Управление статусом подключений
    void acceptConnection(ConnectionRequestDTO request);
    void rejectConnection(ConnectionRequestDTO request);
    void disconnect(String deviceIp);

    // Проверки статуса
    boolean isConnectionPending(String deviceIp);
    boolean isConnectionEstablished(String deviceIp);
    ConnectionRequestDTO getConnectionStatus(String deviceIp);

    // Утилиты
    ConnectionRequestDTO createConnectionRequest(DeviceDTO remoteDevice, ConnectionRequestDTO.RequestStatus status);

    interface ConnectionListener {
        void onConnectionRequested(ConnectionRequestDTO request);
        void onConnectionAccepted(ConnectionRequestDTO request);
        void onConnectionRejected(ConnectionRequestDTO request);
        void onConnectionEstablished(ConnectionRequestDTO request);
        void onConnectionDisconnected(String deviceIp);
    }
}