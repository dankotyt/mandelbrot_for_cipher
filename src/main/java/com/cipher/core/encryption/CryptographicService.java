package com.cipher.core.encryption;

import com.cipher.core.dto.encryption.EncryptionResult;
import com.cipher.core.dto.encryption.EncryptionDataResult;
import com.cipher.core.service.network.CryptoKeyManager;
import com.cipher.core.utils.EncryptionDataSerializer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
public class CryptographicService {
    private static final Logger logger = LoggerFactory.getLogger(CryptographicService.class);

    @Getter
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    @Getter
    private static final int KEY_SIZE = 256;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;

    private final EncryptionDataSerializer serializer;
    private final CryptoKeyManager cryptoKeyManager;

    public EncryptionDataResult encryptData(EncryptionResult result) throws Exception {
        byte[] imageData = serializer.serializeImage(result.encryptedImage());
        byte[] salt = generateSalt();
        byte[] iv = generateIV();

        InetAddress peerAddress = cryptoKeyManager.getConnectedPeer();

        // Получаем мастер-сид из DH обмена
        byte[] masterSeed = cryptoKeyManager.getMasterSeedFromDH(peerAddress);
        logger.info("Using master seed from DH for peer: {}", peerAddress.getHostAddress());

        SecretKey key = generateKeyFromMasterSeed(masterSeed, salt);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

        byte[] encryptedImageData = cipher.doFinal(imageData);

        return new EncryptionDataResult(encryptedImageData, result.params(), iv, salt);
    }

    public BufferedImage decryptData(EncryptionDataResult encryptedDataResult, InetAddress peerAddress) throws Exception {
        try {
            byte[] encryptedImageData  = encryptedDataResult.encryptedImageData();
            byte[] iv = encryptedDataResult.iv();
            byte[] salt = encryptedDataResult.salt();

            // Получаем мастер-сид из DH обмена
            byte[] masterSeed = cryptoKeyManager.getMasterSeedFromDH(peerAddress);
            logger.info("Using master seed from DH for decryption from peer: {}", peerAddress.getHostAddress());

            SecretKey key = generateKeyFromMasterSeed(masterSeed, salt);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            byte[] decryptedImageData = cipher.doFinal(encryptedImageData);

            // Десериализуем изображение из байтов
            return serializer.deserializeImage(decryptedImageData);

        } catch (Exception e) {
            logger.error("Decryption failed for peer {}: {}", peerAddress.getHostAddress(), e.getMessage());
            throw new Exception("Decryption failed: " + e.getMessage(), e);
        }
    }

    private SecretKey generateKeyFromMasterSeed(byte[] masterSeed, byte[] salt) throws Exception {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(
                    new String(masterSeed, StandardCharsets.UTF_8).toCharArray(),
                    salt,
                    65536, // iterations
                    256    // key length
            );

            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");

        } catch (Exception e) {
            logger.error("Key generation failed: {}", e.getMessage());
            throw new Exception("Failed to generate encryption key: " + e.getMessage(), e);
        }
    }

    private byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private byte[] generateIV() {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    // Метод для проверки возможности шифрования с указанным пиром
    public boolean canEncryptToPeer(InetAddress peerAddress) {
        try {
            cryptoKeyManager.getMasterSeedFromDH(peerAddress);
            return cryptoKeyManager.isConnectedTo(peerAddress);
        } catch (Exception e) {
            logger.warn("Cannot encrypt to peer {}: {}", peerAddress.getHostAddress(), e.getMessage());
            return false;
        }
    }
}
