package com.cipher.core.service.encryption;

import com.cipher.core.model.ECDHKeyPair;

import java.math.BigInteger;

public interface ECDHService {
    ECDHKeyPair generateKeyPair();
    byte[] serializePublicKey(ECDHKeyPair keyPair);
    byte[] serializePublicKey(BigInteger[] publicKey);
    BigInteger[] deserializePublicKey(byte[] bytes);
    void computeSharedSecret(ECDHKeyPair localKeys, byte[] otherPublicKeyBytes);
}