package com.cipher.core.encryption;

import com.cipher.core.dto.EncryptionResult;
import com.cipher.core.dto.neww.EncryptionDataResult;
import com.cipher.core.utils.EncryptionDataSerializer;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger logger = LoggerFactory.getLogger(CryptographicService.class);
    @Getter
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    @Getter
    private static final int KEY_SIZE = 256;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;

    private final EncryptionDataSerializer serializer;

    private byte[] masterSeed;

    public CryptographicService() {
        this.serializer = new EncryptionDataSerializer();
    }

    public void initMasterSeed(byte[] masterSeed) {
        this.masterSeed = masterSeed.clone();
    }

    public SecretKey generateKeyFromSeed(byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(
                toCharArray(masterSeed),
                salt,
                65536,
                KEY_SIZE
        );
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    //todo вместо byte[] использовать передачу параметров через dto
    /**
     * Шифрование данных с использованием AES-GCM
     */
    public EncryptionDataResult encryptData(EncryptionResult result) throws Exception {
        byte[] data = serializer.serialize(result);

        byte[] salt = generateSalt();
        byte[] iv = generateIV();

        //todo на время дебага
        logger.info("masterSeed: {}", masterSeed);

        SecretKey key = generateKeyFromSeed(salt);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

        byte[] encryptedData = cipher.doFinal(data);

        return new EncryptionDataResult(encryptedData, iv, salt);
    }

    /**
     * Дешифрование данных
     */
    /*
    *todo нужно подумать, как передавать masterSeed для оффлайна - то ли
    * внутри бинарника, то ли вводить вручную
    */
    public byte[] decryptData(EncryptionDataResult encryptedResult) throws Exception {
        byte[] data = serializer.deserialize(encryptedResult);

        SecretKey key = generateKeyFromSeed(encryptedResult.salt());

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
