package com.cipher.client.service.chat;

import com.cipher.common.dto.chat.ChatMessageDTO;

public interface ChatService {
    boolean connectToPeer(String peerIp, int port);

    void sendMessage(String message);
    void sendImage(byte[] imageData, String fileName);
    void sendFile(byte[] fileData, String fileName);

    boolean isConnected();
    String getConnectedPeer();
    void disconnect();

    void startListening(int port);
    void stopListening();

    void addListener(ChatListener listener);
    void removeListener(ChatListener listener);

    interface ChatListener {
        void onMessageReceived(ChatMessageDTO message);
        void onImageReceived(ChatMessageDTO imageMessage);
        void onConnectionStatusChanged(boolean connected, String peerInfo);
        void onError(String errorMessage);
        void onIncomingConnection(String peerIp);
    }
}
