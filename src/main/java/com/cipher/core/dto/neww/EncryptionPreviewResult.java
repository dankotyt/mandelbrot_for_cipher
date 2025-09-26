package com.cipher.core.dto.neww;

import java.awt.image.BufferedImage;

public record EncryptionPreviewResult(
        BufferedImage originalImage,      // Оригинальное изображение для показа
        BufferedImage fractalPreview,     // Превью фрактала 720x540
        EncryptionParams params           // Параметры шифрования
) {}
