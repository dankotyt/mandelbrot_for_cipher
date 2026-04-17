package com.cipher.client.utils;

import com.cipher.common.dto.chat.ChatMessageDTO;
import com.cipher.core.service.network.CryptoKeyManager;
import com.cipher.core.service.encryption.util.HKDF;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChatEncryptionUtil {

    private static final String AES_ALGO = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE = 32; // 256 бит
    private static final int GCM_IV_LENGTH = 12; // 96 бит
    private static final int GCM_TAG_LENGTH = 128; // бит

    private final CryptoKeyManager cryptoKeyManager;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Получает ключ AES для данного пира из мастер-сида ECDH.
     * Использует HKDF для вывода ключа.
     */
    private SecretKey deriveAesKey(String peerIp) {
        try {
            InetAddress peerAddress = InetAddress.getByName(peerIp);
            byte[] masterSeed = cryptoKeyManager.getMasterSeedFromDH(peerAddress);
            byte[] keyBytes = HKDF.expand(masterSeed, "chat-encryption".getBytes(StandardCharsets.UTF_8), AES_KEY_SIZE);
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            log.error("Не удалось получить ключ AES для пира {}: {}", peerIp, e.getMessage());
            throw new RuntimeException("Ошибка получения ключа шифрования чата", e);
        }
    }

    public ChatMessageDTO encryptMessage(ChatMessageDTO message, String peerIp) {
        try {
            SecretKey key = deriveAesKey(peerIp);
            byte[] plainData;

            // Подготавливаем данные для шифрования в зависимости от типа
            if (message.isText()) {
                plainData = message.getContent().getBytes(StandardCharsets.UTF_8);
            } else if (message.hasFile()) {
                plainData = message.getFileData(); // для изображений и файлов
            } else {
                return message; // неизвестный тип
            }

            // Генерируем случайный IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGO);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            byte[] ciphertext = cipher.doFinal(plainData);

            // Упаковываем IV и шифротекст вместе
            ByteBuffer buffer = ByteBuffer.allocate(4 + iv.length + ciphertext.length);
            buffer.putInt(iv.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            byte[] encryptedData = buffer.array();

            // Создаём копию сообщения с зашифрованными данными
            return ChatMessageDTO.builder()
                    .timestamp(message.getTimestamp())
                    .sender(message.getSender())
                    .encrypted(true)
                    .type(message.getType())
                    .fileData(encryptedData)   // для текста тоже используем fileData? можно переиспользовать поле
                    .fileName(message.getFileName())
                    .fileSize(encryptedData.length)
                    .build();

        } catch (Exception e) {
            log.error("Ошибка шифрования сообщения: {}", e.getMessage());
            throw new RuntimeException("Ошибка шифрования", e);
        }
    }

    public ChatMessageDTO decryptMessage(ChatMessageDTO message, String peerIp) {
        try {
            if (!message.isEncrypted()) {
                return message; // не зашифровано
            }

            SecretKey key = deriveAesKey(peerIp);
            byte[] encryptedData = message.getFileData(); // данные лежат в fileData

            ByteBuffer buffer = ByteBuffer.wrap(encryptedData);
            int ivLength = buffer.getInt();
            if (ivLength != GCM_IV_LENGTH) {
                throw new IllegalArgumentException("Неверная длина IV");
            }
            byte[] iv = new byte[ivLength];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(AES_ALGO);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            byte[] plainData = cipher.doFinal(ciphertext);

            // Восстанавливаем сообщение
            ChatMessageDTO.ChatMessageDTOBuilder builder = ChatMessageDTO.builder()
                    .timestamp(message.getTimestamp())
                    .sender(message.getSender())
                    .encrypted(false) // после расшифровки сбрасываем флаг
                    .type(message.getType())
                    .fileName(message.getFileName());

            if (message.isText()) {
                String text = new String(plainData, StandardCharsets.UTF_8);
                builder.content(text);
            } else {
                builder.fileData(plainData)
                        .fileSize(plainData.length);
            }

            return builder.build();

        } catch (Exception e) {
            log.error("Ошибка дешифрования сообщения: {}", e.getMessage());
            throw new RuntimeException("Ошибка дешифрования", e);
        }
    }

    public void removeSessionKey(String peerIp) {
        // Ничего не делаем, ключи управляются CryptoKeyManager
    }

    public void clearAllSessionKeys() {
        // Ничего не делаем
    }
}