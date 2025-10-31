package com.cipher.core.model;

import com.cipher.common.DHCryptoParams;
import lombok.Getter;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;

@Getter
public class DHKeyExchange {
    private final BigInteger privateKey;
    private final BigInteger publicKey;
    private BigInteger sharedSecret;
    private final Instant creationTime;
    private volatile boolean valid = true;
    private byte[] sharedSecretBytes;

    public DHKeyExchange() {
        SecureRandom random = new SecureRandom();
        // Генерируем приватный ключ (256 бит для безопасности)
        this.privateKey = new BigInteger(256, random);
        // Вычисляем публичный ключ: g^privateKey mod p
        this.publicKey = DHCryptoParams.DH_G.modPow(privateKey, DHCryptoParams.DH_P);
        this.creationTime = Instant.now();
    }

    public byte[] getPublicKeyBytes() {
        return publicKey.toByteArray();
    }

    public void computeSharedSecret(BigInteger otherPublicKey) {
        this.sharedSecret = otherPublicKey.modPow(privateKey, DHCryptoParams.DH_P);
        this.sharedSecretBytes = sharedSecret.toByteArray();
    }

    public byte[] getSharedSecretBytes() {
        return sharedSecretBytes != null ? sharedSecretBytes.clone() : null;
    }

    public void invalidate() {
        this.valid = false;
        // Очищаем чувствительные данные из памяти
        if (sharedSecretBytes != null) {
            Arrays.fill(sharedSecretBytes, (byte) 0);
        }
        sharedSecretBytes = null;
        sharedSecret = null;
    }

    public static BigInteger publicKeyFromBytes(byte[] bytes) {
        return new BigInteger(bytes);
    }
}
