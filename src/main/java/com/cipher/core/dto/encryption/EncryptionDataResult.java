package com.cipher.core.dto.encryption;

@Deprecated
public record EncryptionDataResult(
        byte[] encryptedImageData,
        EncryptionParams params,
        byte[] iv,
        byte[] salt
) {}
