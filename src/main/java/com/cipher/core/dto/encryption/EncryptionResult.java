package com.cipher.core.dto.encryption;

import java.awt.image.BufferedImage;

@Deprecated
public record EncryptionResult(
        BufferedImage encryptedImage,
        EncryptionParams params
) {}
