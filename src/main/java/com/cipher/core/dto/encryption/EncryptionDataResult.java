package com.cipher.core.dto.encryption;

public record EncryptionDataResult(
        byte[] encryptedData,
        byte[] iv,
        byte[] salt
) {}
