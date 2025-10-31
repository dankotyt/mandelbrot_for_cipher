package com.cipher.core.service.network;

import com.cipher.core.dto.ConnectionRequestDTO;
import com.cipher.core.dto.DeviceDTO;

import java.net.InetAddress;

public interface ConnectionService {

    void addListener(ConnectionListener listener);
    void removeListener(ConnectionListener listener);
    void sendConnectionRequest(DeviceDTO toDevice);
    void acceptConnectionRequest(ConnectionRequestDTO request);
    void rejectConnectionRequest(ConnectionRequestDTO request);
    void checkIncomingRequests();
    boolean performKeyExchange(InetAddress peerAddress);

    interface ConnectionListener {
        void onRequestReceived(ConnectionRequestDTO request);
        void onRequestAccepted(ConnectionRequestDTO request);
        void onRequestRejected(ConnectionRequestDTO request);
    }
}
