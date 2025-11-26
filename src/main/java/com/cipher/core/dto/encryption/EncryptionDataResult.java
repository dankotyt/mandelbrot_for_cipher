package com.cipher.core.dto.encryption;

public record EncryptionDataResult(
        byte[] encryptedImageData,
        EncryptionParams params,
        byte[] iv,
        byte[] salt
) {}
