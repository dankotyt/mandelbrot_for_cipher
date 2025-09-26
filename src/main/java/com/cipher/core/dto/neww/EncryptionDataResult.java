package com.cipher.core.dto.neww;

public record EncryptionDataResult(
        byte[] encryptedData,
        byte[] iv,
        byte[] salt
) {}
