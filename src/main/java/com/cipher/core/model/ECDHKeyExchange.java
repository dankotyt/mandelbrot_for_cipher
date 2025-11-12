package com.cipher.core.model;

import com.cipher.common.utils.ECDHCryptoParams;
import lombok.Getter;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;

@Getter
public class ECDHKeyExchange {
    private final BigInteger privateKey;
    private final BigInteger[] publicKey; // [x, y]
    private BigInteger[] sharedSecret; // [x, y]
    private final Instant creationTime;
    private volatile boolean valid = true;
    private byte[] sharedSecretBytes;

    public ECDHKeyExchange() {
        SecureRandom random = new SecureRandom();
        this.privateKey = generatePrivateKey(random);
        this.publicKey = scalarMultiply(ECDHCryptoParams.GX, ECDHCryptoParams.GY, privateKey);
        this.creationTime = Instant.now();
    }

    private BigInteger generatePrivateKey(SecureRandom random) {
        BigInteger privateKey;
        do {
            privateKey = new BigInteger(256, random);
        } while (privateKey.compareTo(ECDHCryptoParams.N) >= 0 || privateKey.equals(BigInteger.ZERO));
        return privateKey;
    }

    public byte[] getPublicKeyBytes() {
        // Сериализуем обе координаты: сначала x, потом y
        byte[] xBytes = publicKey[0].toByteArray();
        byte[] yBytes = publicKey[1].toByteArray();
        byte[] result = new byte[4 + xBytes.length + 4 + yBytes.length];

        System.arraycopy(intToBytes(xBytes.length), 0, result, 0, 4);
        System.arraycopy(xBytes, 0, result, 4, xBytes.length);
        System.arraycopy(intToBytes(yBytes.length), 0, result, 4 + xBytes.length, 4);
        System.arraycopy(yBytes, 0, result, 4 + xBytes.length + 4, yBytes.length);

        return result;
    }

    public void computeSharedSecret(byte[] otherPublicKeyBytes) {
        // Десериализуем публичный ключ
        BigInteger[] otherPublicKey = publicKeyFromBytes(otherPublicKeyBytes);
        this.sharedSecret = scalarMultiply(otherPublicKey[0], otherPublicKey[1], privateKey);
        this.sharedSecretBytes = sharedSecretToBytes(sharedSecret);
    }

    public byte[] getSharedSecretBytes() {
        return sharedSecretBytes != null ? sharedSecretBytes.clone() : null;
    }

    public void invalidate() {
        this.valid = false;
        if (sharedSecretBytes != null) {
            Arrays.fill(sharedSecretBytes, (byte) 0);
        }
        sharedSecretBytes = null;
        sharedSecret = null;
    }

    public static BigInteger[] publicKeyFromBytes(byte[] bytes) {
        int xLength = bytesToInt(bytes, 0);
        BigInteger x = new BigInteger(Arrays.copyOfRange(bytes, 4, 4 + xLength));

        int yLength = bytesToInt(bytes, 4 + xLength);
        BigInteger y = new BigInteger(Arrays.copyOfRange(bytes, 4 + xLength + 4, 4 + xLength + 4 + yLength));

        return new BigInteger[]{x, y};
    }

    // ECDH математические операции
    private BigInteger[] scalarMultiply(BigInteger x1, BigInteger y1, BigInteger k) {
        BigInteger x = BigInteger.ZERO;
        BigInteger y = BigInteger.ZERO;
        BigInteger currentX = x1;
        BigInteger currentY = y1;

        for (int i = 0; i < k.bitLength(); i++) {
            if (k.testBit(i)) {
                if (x.equals(BigInteger.ZERO)) {
                    x = currentX;
                    y = currentY;
                } else {
                    BigInteger[] sum = pointAdd(x, y, currentX, currentY);
                    x = sum[0];
                    y = sum[1];
                }
            }
            BigInteger[] doubled = pointDouble(currentX, currentY);
            currentX = doubled[0];
            currentY = doubled[1];
        }

        return new BigInteger[]{x, y};
    }

    private BigInteger[] pointDouble(BigInteger x, BigInteger y) {
        if (y.equals(BigInteger.ZERO)) {
            return new BigInteger[]{BigInteger.ZERO, BigInteger.ZERO};
        }

        BigInteger slope = x.pow(2).multiply(BigInteger.valueOf(3)).add(ECDHCryptoParams.A)
                .multiply(y.multiply(BigInteger.valueOf(2)).modInverse(ECDHCryptoParams.P))
                .mod(ECDHCryptoParams.P);

        BigInteger x3 = slope.pow(2).subtract(x.multiply(BigInteger.valueOf(2))).mod(ECDHCryptoParams.P);
        BigInteger y3 = slope.multiply(x.subtract(x3)).subtract(y).mod(ECDHCryptoParams.P);

        return new BigInteger[]{x3, y3};
    }

    private BigInteger[] pointAdd(BigInteger x1, BigInteger y1, BigInteger x2, BigInteger y2) {
        if (x1.equals(x2) && y1.equals(y2)) {
            return pointDouble(x1, y1);
        }

        BigInteger slope = y2.subtract(y1)
                .multiply(x2.subtract(x1).modInverse(ECDHCryptoParams.P))
                .mod(ECDHCryptoParams.P);

        BigInteger x3 = slope.pow(2).subtract(x1).subtract(x2).mod(ECDHCryptoParams.P);
        BigInteger y3 = slope.multiply(x1.subtract(x3)).subtract(y1).mod(ECDHCryptoParams.P);

        return new BigInteger[]{x3, y3};
    }

    private byte[] sharedSecretToBytes(BigInteger[] sharedSecret) {
        // Используем обе координаты для большего энтропии
        byte[] xBytes = sharedSecret[0].toByteArray();
        byte[] yBytes = sharedSecret[1].toByteArray();
        byte[] result = new byte[xBytes.length + yBytes.length];
        System.arraycopy(xBytes, 0, result, 0, xBytes.length);
        System.arraycopy(yBytes, 0, result, xBytes.length, yBytes.length);
        return result;
    }

    private byte[] intToBytes(int value) {
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value
        };
    }

    private static int bytesToInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) |
                ((bytes[offset + 1] & 0xFF) << 16) |
                ((bytes[offset + 2] & 0xFF) << 8) |
                (bytes[offset + 3] & 0xFF);
    }
}
