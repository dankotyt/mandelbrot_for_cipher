package com.dankotyt.core.service.encryption.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class HKDF {
    private static final String HMAC_ALGO = "HmacSHA256";

    public static byte[] extract(byte[] salt, byte[] ikm) throws NoSuchAlgorithmException, InvalidKeyException {
        if (ikm == null) {
            throw new IllegalArgumentException("IKM (input keying material) cannot be null");
        }
        Mac mac = Mac.getInstance(HMAC_ALGO);
        if (salt == null || salt.length == 0) {
            salt = new byte[32];
        }
        mac.init(new SecretKeySpec(salt, HMAC_ALGO));
        return mac.doFinal(ikm);
    }

    public static byte[] expand(byte[] prk, byte[] info, int length) throws NoSuchAlgorithmException, InvalidKeyException {
        if (prk == null) {
            throw new IllegalArgumentException("PRK (pseudo-random key) cannot be null");
        }
        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(new SecretKeySpec(prk, HMAC_ALGO));
        byte[] result = new byte[length];
        byte[] t = new byte[0];
        int counter = 1;
        int offset = 0;
        while (offset < length) {
            mac.update(t);
            mac.update(info);
            mac.update((byte) counter++);
            t = mac.doFinal();
            int toCopy = Math.min(t.length, length - offset);
            System.arraycopy(t, 0, result, offset, toCopy);
            offset += toCopy;
        }
        return result;
    }
}
