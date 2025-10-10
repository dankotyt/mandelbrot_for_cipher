package com.cipher.core.dto;

import com.cipher.core.dto.neww.EncryptionParams;

import java.awt.image.BufferedImage;

public record EncryptionResult(
        BufferedImage segmentedImage,
        BufferedImage fractalImage,
        EncryptionParams params
) {}
