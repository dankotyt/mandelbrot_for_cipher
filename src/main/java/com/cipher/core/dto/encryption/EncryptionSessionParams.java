package com.cipher.core.dto.encryption;

public record EncryptionSessionParams(byte[] encryptedMasterSeed, byte[] iv, byte[] salt,
                                      String algorithm, int keySize, long timestamp) {
}
