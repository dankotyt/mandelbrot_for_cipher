package com.cipher.core.dto;

public record EncryptionSessionParams(byte[] encryptedMasterSeed, byte[] iv, byte[] salt,
                                      String algorithm, int keySize, long timestamp) {
}
