package com.dankotyt.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.net.InetAddress;

/**
 * Единый пакет для подписанных сообщений при соединении.
 * Содержит все необходимое для обмена ключами с проверкой подписи.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignedConnectionPacket implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    // Тип пакета (REQUEST или RESPONSE)
    private PacketType type;

    // Общие поля
    private InetAddress senderAddress;
    private String senderName;
    private long timestamp;

    // Криптографические ключи
    private byte[] dhPublicKey;        // Публичный ключ для Диффи-Хеллмана (из ECDHKeyExchange)
    private byte[] signaturePublicKey; // Публичный ключ для проверки подписи

    // Поля для ответа (RESPONSE)
    private boolean accepted;
    private String message;
    private long cooldownUntil; // Время разблокировки при отказе

    // Цифровая подпись всего пакета (кроме самого поля signature)
    private byte[] signature;

    public enum PacketType {
        REQUEST, RESPONSE
    }

    /**
     * Получить данные для подписи (все поля кроме signature)
     */
    public byte[] getDataToSign() {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);

            dos.writeUTF(type.name());
            dos.writeUTF(senderAddress.getHostAddress());
            dos.writeUTF(senderName != null ? senderName : "");
            dos.writeLong(timestamp);

            dos.writeInt(dhPublicKey != null ? dhPublicKey.length : 0);
            if (dhPublicKey != null) dos.write(dhPublicKey);

            dos.writeInt(signaturePublicKey != null ? signaturePublicKey.length : 0);
            if (signaturePublicKey != null) dos.write(signaturePublicKey);

            dos.writeBoolean(accepted);
            dos.writeUTF(message != null ? message : "");
            dos.writeLong(cooldownUntil);

            dos.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize packet for signing", e);
        }
    }

    /**
     * Проверка валидности временной метки (не старше 5 минут)
     */
    public boolean isTimestampValid() {
        long now = System.currentTimeMillis();
        return Math.abs(now - timestamp) < 300000; // 5 минут
    }

    /**
     * Создать пакет-запрос
     */
    public static SignedConnectionPacket createRequest(
            InetAddress senderAddress,
            String senderName,
            byte[] dhPublicKey,
            byte[] signaturePublicKey) {

        return SignedConnectionPacket.builder()
                .type(PacketType.REQUEST)
                .senderAddress(senderAddress)
                .senderName(senderName)
                .timestamp(System.currentTimeMillis())
                .dhPublicKey(dhPublicKey)
                .signaturePublicKey(signaturePublicKey)
                .accepted(false)
                .message("")
                .cooldownUntil(0)
                .build();
    }

    /**
     * Создать пакет-ответ
     */
    public static SignedConnectionPacket createResponse(
            InetAddress senderAddress,
            boolean accepted,
            String message,
            byte[] dhPublicKey,
            byte[] signaturePublicKey,
            long cooldownUntil) {

        return SignedConnectionPacket.builder()
                .type(PacketType.RESPONSE)
                .senderAddress(senderAddress)
                .senderName("")
                .timestamp(System.currentTimeMillis())
                .dhPublicKey(dhPublicKey)
                .signaturePublicKey(signaturePublicKey)
                .accepted(accepted)
                .message(message)
                .cooldownUntil(cooldownUntil)
                .build();
    }
}