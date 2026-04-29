package com.dankotyt.core.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;

/**
 * Пара ключей ECDH: приватный, публичный (точка [x,y]) и опционально вычисленный общий секрет.
 * Поддерживает безопасное стирание секретных данных методом {@link #invalidate()}.
 *
 * @author dankotyt
 * @since 1.1.0
 */
@Getter
public class ECDHKeyPair {
    private final BigInteger privateKey;
    private final BigInteger[] publicKey; // [x, y]
    @Setter
    private BigInteger[] sharedSecret; // [x, y]
    private final Instant creationTime;
    @Setter
    private byte[] sharedSecretBytes;

    /**
     * Создаёт пару ключей с заданным приватным ключом, публичной точкой и временем создания.
     *
     * @param privateKey  приватный ключ (скаляр)
     * @param publicKey   публичный ключ как массив из двух BigInteger [x, y]
     * @param creationTime момент создания пары
     */
    public ECDHKeyPair(BigInteger privateKey, BigInteger[] publicKey, Instant creationTime) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.creationTime = creationTime;
    }

    /**
     * Безвозвратно затирает общий секрет и его байтовое представление, обнуляя ссылки.
     */
    public void invalidate() {
        if (sharedSecretBytes != null) {
            Arrays.fill(sharedSecretBytes, (byte) 0);
        }
        sharedSecretBytes = null;
        sharedSecret = null;
    }
}