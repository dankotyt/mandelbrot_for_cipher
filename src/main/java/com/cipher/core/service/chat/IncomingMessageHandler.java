package com.cipher.core.service.chat;

import com.cipher.common.dto.chat.ChatMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * IncomingMessageHandler - обработчик ВХОДЯЩИХ сообщений
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncomingMessageHandler {

    private final ExecutorService messageProcessor = Executors.newCachedThreadPool();
    private final List<MessageListener> listeners = new ArrayList<>();

    /**
     * Обработка входящего сообщения
     */
    public void handleIncomingMessage(String peerIp, ChatMessageDTO message) {
        messageProcessor.execute(() -> {
            try {
                switch (message.getType()) {
                    case TEXT:
                        notifyMessageReceived(message);
                        log.debug("💬 Получено сообщение от {}: {}", peerIp, message.getContent());
                        break;
                    case IMAGE:
                        ChatMessageDTO decodedImage = message.withFileData(decodeBinaryData(message.getFileData()));
                        notifyImageReceived(decodedImage);
                        log.info("🖼️ Получено изображение от {}: {} ({} bytes)",
                                peerIp, message.getFileName(), message.getFileData().length);
                        break;
                    case FILE:
                        ChatMessageDTO decodedFile = message.withFileData(decodeBinaryData(message.getFileData()));
                        notifyFileReceived(decodedFile);
                        log.info("📎 Получен файл от {}: {} ({} bytes)",
                                peerIp, message.getFileName(), message.getFileData().length);
                        break;
                }
            } catch (Exception e) {
                log.error("❌ Ошибка обработки входящего сообщения от {}: {}", peerIp, e.getMessage());
            }
        });
    }

    private byte[] decodeBinaryData(byte[] encodedData) {
        if (encodedData == null) return null;
        return java.util.Base64.getDecoder().decode(encodedData);
    }

    // Методы для слушателей
    public void addListener(MessageListener listener) {
        listeners.add(listener);
    }

    public void removeListener(MessageListener listener) {
        listeners.remove(listener);
    }

    private void notifyMessageReceived(ChatMessageDTO message) {
        for (MessageListener listener : listeners) {
            listener.onMessageReceived(message);
        }
    }

    private void notifyImageReceived(ChatMessageDTO message) {
        for (MessageListener listener : listeners) {
            listener.onImageReceived(message);
        }
    }

    private void notifyFileReceived(ChatMessageDTO message) {
        for (MessageListener listener : listeners) {
            listener.onFileReceived(message);
        }
    }

    public interface MessageListener {
        void onMessageReceived(ChatMessageDTO message);
        void onImageReceived(ChatMessageDTO message);
        void onFileReceived(ChatMessageDTO message);
    }
}