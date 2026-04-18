package com.dankotyt.core.service.network;

import com.dankotyt.core.model.SignedConnectionPacket;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Getter
@Component
public class DigitalSignatureService {
    private static final Logger logger = LoggerFactory.getLogger(DigitalSignatureService.class);
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final String KEY_ALGORITHM = "EC";
    private static final String CURVE = "secp256r1";

    private KeyPair signatureKeyPair;

    public DigitalSignatureService() {
        generateSignatureKeyPair();
    }

    /**
     * Генерирует новую пару ключей для подписи
     */
    public void generateSignatureKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            ECGenParameterSpec ecSpec = new ECGenParameterSpec(CURVE);
            keyGen.initialize(ecSpec, new SecureRandom());
            this.signatureKeyPair = keyGen.generateKeyPair();
            logger.info("✅ Сгенерирована новая пара ключей для цифровой подписи");
        } catch (Exception e) {
            logger.error("❌ Ошибка генерации ключей подписи: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось сгенерировать ключи подписи", e);
        }
    }

    /**
     * Подписывает данные
     */
    public byte[] sign(byte[] data) {
        try {
            Signature ecdsaSign = Signature.getInstance(SIGNATURE_ALGORITHM);
            ecdsaSign.initSign(signatureKeyPair.getPrivate());
            ecdsaSign.update(data);
            return ecdsaSign.sign();
        } catch (Exception e) {
            logger.error("❌ Ошибка создания подписи: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось создать подпись", e);
        }
    }

    /**
     * Проверяет подпись
     */
    public boolean verify(byte[] data, byte[] signature, PublicKey publicKey) {
        try {
            Signature ecdsaVerify = Signature.getInstance(SIGNATURE_ALGORITHM);
            ecdsaVerify.initVerify(publicKey);
            ecdsaVerify.update(data);
            return ecdsaVerify.verify(signature);
        } catch (Exception e) {
            logger.error("❌ Ошибка проверки подписи: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Подписывает ConnectionPacket
     */
    public void signPacket(SignedConnectionPacket packet) {
        byte[] dataToSign = packet.getDataToSign();
        byte[] signature = sign(dataToSign);
        packet.setSignature(signature);
    }

    /**
     * Проверяет подпись ConnectionPacket
     */
    public boolean verifyPacket(SignedConnectionPacket packet) {
        if (!packet.isTimestampValid()) {
            logger.warn("⚠️ Пакет имеет невалидную временную метку");
            return false;
        }

        byte[] dataToSign = packet.getDataToSign();
        PublicKey publicKey = bytesToPublicKey(packet.getSignaturePublicKey());
        return verify(dataToSign, packet.getSignature(), publicKey);
    }

    /**
     * Конвертирует публичный ключ в байты
     */
    public byte[] publicKeyToBytes(PublicKey publicKey) {
        return publicKey.getEncoded();
    }

    /**
     * Конвертирует байты в публичный ключ
     */
    public PublicKey bytesToPublicKey(byte[] keyBytes) {
        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            logger.error("❌ Ошибка конвертации байт в PublicKey: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to convert bytes to PublicKey", e);
        }
    }

    /**
     * Экспортирует публичный ключ в Base64 строку (для отображения)
     */
    public String exportPublicKeyAsString() {
        return Base64.getEncoder().encodeToString(signatureKeyPair.getPublic().getEncoded());
    }
}