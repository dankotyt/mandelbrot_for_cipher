package com.cipher.core.service.chat;

import com.cipher.common.dto.chat.ChatMessageDTO;
import com.cipher.common.utils.NetworkConstants;
import com.cipher.core.service.network.ConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MessageService - "Сервис обмена сообщениями"
 * Что делает: Отправка и прием сообщений по УЖЕ установленным соединениям
 * Только работа с сообщениями, без управления соединениями
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final ConnectionManager connectionManager;
    private final ConcurrentHashMap<String, ObjectOutputStream> peerOutputStreams = new ConcurrentHashMap<>();

    /**
     * Отправка текстового сообщения
     */
    public boolean sendMessage(String peerIp, String message) {
        try {
            if (!connectionManager.isConnectedTo(InetAddress.getByName(peerIp))) {
                log.error("❌ Нет активного соединения с: {}", peerIp);
                return false;
            }

            ChatMessageDTO chatMessage = ChatMessageDTO.builder()
                    .content(message)
                    .type(ChatMessageDTO.MessageType.TEXT)
                    .timestamp(LocalDateTime.now())
                    .sender(getLocalPeerName())
                    .encrypted(false)
                    .build();

            return sendToPeer(peerIp, chatMessage);

        } catch (Exception e) {
            log.error("❌ Ошибка отправки сообщения к {}: {}", peerIp, e.getMessage());
            return false;
        }
    }

    /**
     * Отправка изображения
     */
    public boolean sendImage(String peerIp, byte[] imageData, String fileName) {
        try {
            if (!connectionManager.isConnectedTo(InetAddress.getByName(peerIp))) {
                log.error("❌ Нет активного соединения с: {}", peerIp);
                return false;
            }

            ChatMessageDTO imageMessage = ChatMessageDTO.builder()
                    .type(ChatMessageDTO.MessageType.IMAGE)
                    .fileName(fileName)
                    .fileData(encodeBinaryData(imageData))
                    .fileSize(imageData.length)
                    .timestamp(LocalDateTime.now())
                    .sender(getLocalPeerName())
                    .encrypted(false)
                    .build();

            return sendToPeer(peerIp, imageMessage);

        } catch (Exception e) {
            log.error("❌ Ошибка отправки изображения к {}: {}", peerIp, e.getMessage());
            return false;
        }
    }

    /**
     * Отправка файла
     */
    public boolean sendFile(String peerIp, byte[] fileData, String fileName) {
        try {
            if (!connectionManager.isConnectedTo(InetAddress.getByName(peerIp))) {
                log.error("❌ Нет активного соединения с: {}", peerIp);
                return false;
            }

            ChatMessageDTO fileMessage = ChatMessageDTO.builder()
                    .type(ChatMessageDTO.MessageType.FILE)
                    .fileName(fileName)
                    .fileData(encodeBinaryData(fileData))
                    .fileSize(fileData.length)
                    .timestamp(LocalDateTime.now())
                    .sender(getLocalPeerName())
                    .encrypted(false)
                    .build();

            return sendToPeer(peerIp, fileMessage);

        } catch (Exception e) {
            log.error("❌ Ошибка отправки файла к {}: {}", peerIp, e.getMessage());
            return false;
        }
    }

    private byte[] encodeBinaryData(byte[] data) {
        if (data == null) return null;
        return java.util.Base64.getEncoder().encode(data);
    }

    private boolean sendToPeer(String peerIp, ChatMessageDTO message) {
        try {
            Socket peerSocket = connectionManager.getSocketForPeer(InetAddress.getByName(peerIp));
            if (peerSocket == null || peerSocket.isClosed()) {
                log.error("❌ Сокет не доступен для: {}", peerIp);
                return false;
            }

            ObjectOutputStream outputStream = getOutputStreamForPeer(peerIp, peerSocket);

            DataOutputStream dataOut = new DataOutputStream(peerSocket.getOutputStream());
            dataOut.writeUTF(NetworkConstants.CHAT_MESSAGE_TYPE);
            dataOut.flush();

            outputStream.writeObject(message);
            outputStream.flush();

            log.debug("✅ Сообщение отправлено к: {}", peerIp);
            return true;

        } catch (IOException e) {
            log.error("❌ Ошибка отправки к {}: {}", peerIp, e.getMessage());
            peerOutputStreams.remove(peerIp);
            return false;
        }
    }

    private ObjectOutputStream getOutputStreamForPeer(String peerIp, Socket socket) throws IOException {
        return peerOutputStreams.computeIfAbsent(peerIp, k -> {
            try {
                return new ObjectOutputStream(socket.getOutputStream());
            } catch (IOException e) {
                throw new RuntimeException("Не удалось создать OutputStream", e);
            }
        });
    }

    private String getLocalPeerName() {
        return System.getProperty("user.name", "Peer");
    }
}