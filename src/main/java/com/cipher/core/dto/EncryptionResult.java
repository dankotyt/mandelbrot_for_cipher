package com.cipher.core.dto;

//public record EncryptionResult(byte[] encryptedData, byte[] iv, byte[] salt) {
//}

import com.cipher.core.dto.neww.EncryptionParams;

import java.awt.image.BufferedImage;

public record EncryptionResult(
        BufferedImage segmentedImage,
        BufferedImage fractalImage,
        EncryptionParams params
) {}
