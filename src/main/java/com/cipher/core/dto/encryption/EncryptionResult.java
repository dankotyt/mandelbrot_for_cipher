package com.cipher.core.dto.encryption;

import java.awt.image.BufferedImage;

public record EncryptionResult(
        BufferedImage segmentedImage,
        BufferedImage fractalImage,
        EncryptionParams params
) {}
