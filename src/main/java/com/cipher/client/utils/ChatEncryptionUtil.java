package com.cipher.client.utils;

import com.cipher.common.dto.chat.ChatMessageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ChatEncryptionUtil {

    /**
     * Для чата используем простое кодирование Base64 вместо настоящего шифрования
     * так как основное содержимое уже зашифровано
     */
    public ChatMessageDTO encryptMessage(ChatMessageDTO message, String peerIp) {
        try {
            // Не шифруем, просто помечаем и кодируем бинарные данные
            return ChatMessageDTO.builder()
                    .content(message.getContent()) // текст не шифруем
                    .timestamp(message.getTimestamp())
                    .sender(message.getSender())
                    .encrypted(false) // помечаем как нешифрованное, т.к. основное содержимое уже защищено
                    .type(message.getType())
                    .fileData(encodeBinaryData(message.getFileData()))
                    .fileName(message.getFileName())
                    .fileSize(message.getFileSize())
                    .build();

        } catch (Exception e) {
            log.error("Ошибка обработки сообщения: {}", e.getMessage());
            return message;
        }
    }

    public ChatMessageDTO decryptMessage(ChatMessageDTO message, String peerIp) {
        try {
            // Просто декодируем бинарные данные
            return ChatMessageDTO.builder()
                    .content(message.getContent())
                    .timestamp(message.getTimestamp())
                    .sender(message.getSender())
                    .encrypted(false)
                    .type(message.getType())
                    .fileData(decodeBinaryData(message.getFileData()))
                    .fileName(message.getFileName())
                    .fileSize(message.getFileSize())
                    .build();

        } catch (Exception e) {
            log.error("Ошибка обработки сообщения: {}", e.getMessage());
            return message;
        }
    }

    private byte[] encodeBinaryData(byte[] data) {
        if (data == null) return null;
        // Простое кодирование для передачи по сети
        return java.util.Base64.getEncoder().encode(data);
    }

    private byte[] decodeBinaryData(byte[] encodedData) {
        if (encodedData == null) return null;
        return java.util.Base64.getDecoder().decode(encodedData);
    }

    // Остальные методы можно оставить пустыми или удалить
    public void removeSessionKey(String peerIp) {
        // не используется
    }

    public void clearAllSessionKeys() {
        // не используется
    }
}
