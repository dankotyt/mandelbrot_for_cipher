package com.cipher.client.utils;

import org.bitcoinj.crypto.MnemonicCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class KeysUtils {
    private static final Logger logger = LoggerFactory.getLogger(KeysUtils.class);

    public record CryptoKeys(PrivateKey privateKey, PublicKey publicKey, String userId) {}

    public static CryptoKeys createKeysFromWords(List<String> words) throws NoSuchAlgorithmException, GeneralSecurityException {
        byte[] masterSeed = MnemonicCode.toSeed(words, ""); // 64 bytes
        Arrays.fill(masterSeed, 31, 63, (byte) 0);
        try {
            // Для EdDSA используем первые 32 байта из seed
            byte[] keyMaterial = Arrays.copyOfRange(masterSeed, 0, 32);
            KeyPair keyPair = generateKeyPairFromSeed(keyMaterial);
            PublicKey publicKey = keyPair.getPublic();
            String userId = createUserId(publicKey);

            logger.info("Generated PublicKey: " + Base64.getEncoder().encodeToString(publicKey.getEncoded()));
            logger.info("Generated UserId: " + userId);
            logger.info("Seed words: " + String.join(" ", words));

            return new CryptoKeys(keyPair.getPrivate(), publicKey, userId);
        } finally {
            Arrays.fill(masterSeed, (byte) 0); // Стираем мастер-сид из памяти
        }
    }

    private static KeyPair generateKeyPairFromSeed(byte[] seed) throws NoSuchAlgorithmException {
        SecureRandom deterministicRandom = SecureRandom.getInstance("SHA1PRNG");
        deterministicRandom.setSeed(seed);

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EdDSA");
        keyPairGenerator.initialize(255, deterministicRandom);
        return keyPairGenerator.generateKeyPair();
    }

    private static String createUserId(PublicKey publicKey) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(publicKey.getEncoded());
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Arrays.copyOfRange(hash, 0, 16));
    }

    public static String getPublicKeyBase64(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }
}
