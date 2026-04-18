package com.dankotyt.core.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;

@Getter
public class ECDHKeyPair {
    private final BigInteger privateKey;
    private final BigInteger[] publicKey; // [x, y]
    @Setter
    private BigInteger[] sharedSecret; // [x, y]
    private final Instant creationTime;
    @Setter
    private byte[] sharedSecretBytes;

    public ECDHKeyPair(BigInteger privateKey, BigInteger[] publicKey, Instant creationTime) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.creationTime = creationTime;
    }

    public void invalidate() {
        if (sharedSecretBytes != null) {
            Arrays.fill(sharedSecretBytes, (byte) 0);
        }
        sharedSecretBytes = null;
        sharedSecret = null;
    }
}