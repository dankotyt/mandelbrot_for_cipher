package com.cipher.core.encryption;

import com.cipher.core.dto.EncryptionResult;
import com.cipher.core.dto.neww.EncryptionDataResult;
import lombok.Getter;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

@Component
public class CryptographicService {
    @Getter
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    @Getter
    private static final int KEY_SIZE = 256;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;

    public SecretKey generateKeyFromSeed(byte[] masterSeed, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(
                toCharArray(masterSeed),
                salt,
                65536,
                KEY_SIZE
        );
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    /**
     * Шифрование данных с использованием AES-GCM
     */
    public EncryptionDataResult encryptData(byte[] data, byte[] masterSeed) throws Exception {
        byte[] salt = generateSalt();
        byte[] iv = generateIV();

        SecretKey key = generateKeyFromSeed(masterSeed, salt);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

        byte[] encryptedData = cipher.doFinal(data);

        return new EncryptionDataResult(encryptedData, iv, salt);
    }

    /**
     * Дешифрование данных
     */
    public byte[] decryptData(EncryptionDataResult encryptedResult, byte[] masterSeed) throws Exception {
        SecretKey key = generateKeyFromSeed(masterSeed, encryptedResult.salt());

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, encryptedResult.iv());
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

        return cipher.doFinal(encryptedResult.encryptedData());
    }

    public byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    public byte[] generateIV() {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    private char[] toCharArray(byte[] bytes) {
        char[] chars = new char[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            chars[i] = (char) (bytes[i] & 0xFF);
        }
        return chars;
    }
}
