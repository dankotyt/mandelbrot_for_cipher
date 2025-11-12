package com.cipher.client.service.chat.impl;

import com.cipher.client.service.chat.ChatService;
import com.cipher.client.utils.ChatEncryptionUtil;
import com.cipher.common.dto.chat.ChatMessageDTO;
import com.cipher.core.service.chat.IncomingMessageHandler;
import com.cipher.core.service.chat.MessageService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class P2PChatServiceImpl implements ChatService {

    private final MessageService messageService;
    private final IncomingMessageHandler incomingMessageHandler;
    private final List<ChatListener> listeners = new ArrayList<>();

    private String currentPeerIp;
    private final List<ChatMessageDTO> messageHistory = new ArrayList<>();

    @Override
    public boolean connectToPeer(String peerIp) {
        this.currentPeerIp = peerIp;
        log.info("💬 Чат активирован для: {}", peerIp);
        notifyConnectionStatusChanged(true, "Чат подключен к " + peerIp);
        return true;
    }

    @Override
    public void startListening(int port) {
        // В новой архитекции прослушивание уже настроено в NetworkBootstrap
        log.debug("👂 Чат готов к приему сообщений");
    }

    @Override
    public void sendMessage(String message) {
        if (currentPeerIp == null) {
            notifyError("Не выбран собеседник");
            return;
        }

        try {
            // Создаем сообщение для UI
            ChatMessageDTO uiMessage = ChatMessageDTO.builder()
                    .content(message)
                    .type(ChatMessageDTO.MessageType.TEXT)
                    .timestamp(LocalDateTime.now())
                    .sender(getLocalPeerName())
                    .encrypted(false) // В UI показываем незашифрованное
                    .build();

            // Добавляем в историю и уведомляем UI
            messageHistory.add(uiMessage);
            notifyMessageReceived(uiMessage);

            // Отправляем через MessageService (он зашифрует)
            boolean sent = messageService.sendMessage(currentPeerIp, message);

            if (!sent) {
                notifyError("Не удалось отправить сообщение");
            }

        } catch (Exception e) {
            log.error("❌ Ошибка отправки сообщения: {}", e.getMessage());
            notifyError("Ошибка отправки: " + e.getMessage());
        }
    }

    @Override
    public void sendImage(byte[] imageData, String fileName) {
        if (currentPeerIp == null) {
            notifyError("Не выбран собеседник");
            return;
        }

        try {
            // Создаем сообщение для UI
            ChatMessageDTO uiMessage = ChatMessageDTO.builder()
                    .type(ChatMessageDTO.MessageType.IMAGE)
                    .fileName(fileName)
                    .fileData(imageData)
                    .fileSize(imageData.length)
                    .timestamp(LocalDateTime.now())
                    .sender(getLocalPeerName())
                    .encrypted(false)
                    .build();

            messageHistory.add(uiMessage);
            notifyImageReceived(uiMessage);

            // Отправляем через MessageService
            boolean sent = messageService.sendImage(currentPeerIp, imageData, fileName);

            if (!sent) {
                notifyError("Не удалось отправить изображение");
            }

        } catch (Exception e) {
            log.error("❌ Ошибка отправки изображения: {}", e.getMessage());
            notifyError("Ошибка отправки изображения: " + e.getMessage());
        }
    }

    @Override
    public void sendFile(byte[] fileData, String fileName) {
        if (currentPeerIp == null) {
            notifyError("Не выбран собеседник");
            return;
        }

        try {
            ChatMessageDTO uiMessage = ChatMessageDTO.builder()
                    .type(ChatMessageDTO.MessageType.FILE)
                    .fileName(fileName)
                    .fileData(fileData)
                    .fileSize(fileData.length)
                    .timestamp(LocalDateTime.now())
                    .sender(getLocalPeerName())
                    .encrypted(false)
                    .build();

            messageHistory.add(uiMessage);
            notifyFileReceived(uiMessage);

            boolean sent = messageService.sendFile(currentPeerIp, fileData, fileName);

            if (!sent) {
                notifyError("Не удалось отправить файл");
            }

        } catch (Exception e) {
            log.error("❌ Ошибка отправки файла: {}", e.getMessage());
            notifyError("Ошибка отправки файла: " + e.getMessage());
        }
    }

    /**
     * Обработка входящего сообщения (вызывается из MessageService)
     */
    public void handleIncomingMessage(ChatMessageDTO message) {
        // Добавляем в историю и показываем в UI
        messageHistory.add(message);

        switch (message.getType()) {
            case TEXT:
                notifyMessageReceived(message);
                break;
            case IMAGE:
                notifyImageReceived(message);
                break;
            case FILE:
                notifyFileReceived(message);
                break;
        }

        log.debug("💬 Сообщение добавлено в историю: {}", message.getType());
    }

    @Override
    public boolean isConnected() {
        return currentPeerIp != null;
    }

    @Override
    public String getConnectedPeer() {
        return currentPeerIp;
    }

    @Override
    public void disconnect() {
        String disconnectedPeer = currentPeerIp;
        currentPeerIp = null;
        messageHistory.clear();

        notifyConnectionStatusChanged(false, "Чат отключен");
        log.info("💬 Чат отключен от: {}", disconnectedPeer);
    }

    @Override
    public void stopListening() {
        // В новой архитекции ничего не делаем
        log.debug("💬 Прослушивание чата остановлено");
    }

    // 🔴 Управление историей сообщений
    public List<ChatMessageDTO> getMessageHistory() {
        return new ArrayList<>(messageHistory);
    }

    public List<ChatMessageDTO> getMessageHistoryForPeer(String peerIp) {
        return messageHistory.stream()
                .filter(msg -> peerIp.equals(currentPeerIp))
                .toList();
    }

    public void clearHistory() {
        messageHistory.clear();
        log.info("🗑️ История сообщений очищена");
    }

    // 🔴 Методы уведомления слушателей
    private void notifyMessageReceived(ChatMessageDTO message) {
        for (ChatListener listener : listeners) {
            listener.onMessageReceived(message);
        }
    }

    private void notifyImageReceived(ChatMessageDTO message) {
        for (ChatListener listener : listeners) {
            listener.onImageReceived(message);
        }
    }

    private void notifyFileReceived(ChatMessageDTO message) {
        for (ChatListener listener : listeners) {
            listener.onFileReceived(message);
        }
    }

    private void notifyConnectionStatusChanged(boolean connected, String peerInfo) {
        for (ChatListener listener : listeners) {
            listener.onConnectionStatusChanged(connected, peerInfo);
        }
    }

    private void notifyError(String errorMessage) {
        for (ChatListener listener : listeners) {
            listener.onError(errorMessage);
        }
    }

    private void notifyIncomingConnection(String peerIp) {
        for (ChatListener listener : listeners) {
            listener.onIncomingConnection(peerIp);
        }
    }

    @Override
    public void addListener(ChatListener listener) {
        // ✅ Регистрируем слушатель в IncomingMessageHandler
        incomingMessageHandler.addListener(new IncomingMessageHandler.MessageListener() {
            @Override
            public void onMessageReceived(ChatMessageDTO message) {
                listener.onMessageReceived(message);
            }

            @Override
            public void onImageReceived(ChatMessageDTO message) {
                listener.onImageReceived(message);
            }

            @Override
            public void onFileReceived(ChatMessageDTO message) {
                listener.onFileReceived(message);
            }
        });
    }

    @Override
    public void removeListener(ChatListener listener) {
        listeners.remove(listener);
    }

    private String getLocalPeerName() {
        return System.getProperty("user.name", "Локальный пользователь");
    }
}
