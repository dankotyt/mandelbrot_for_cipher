package com.dankotyt.client.service.chat;

import com.dankotyt.common.dto.chat.ChatMessageDTO;

public interface ChatService {
    boolean connectToPeer(String peerIp);

    void sendMessage(String message);
    void sendImage(byte[] imageData, String fileName);
    void sendFile(byte[] fileData, String fileName);

    String getConnectedPeer();
    boolean isConnected();
    void disconnect();

    boolean isActive();

    void addListener(ChatListener listener);
    void removeListener(ChatListener listener);

    interface ChatListener {
        void onMessageReceived(ChatMessageDTO message);
        void onImageReceived(ChatMessageDTO imageMessage);
        void onFileReceived(ChatMessageDTO fileMessage);
        void onConnectionStatusChanged(boolean connected, String peerInfo);
        void onError(String errorMessage);
        void onIncomingConnection(String peerIp);
        void onIncomingChatConnection(String peerIp);
    }
}
