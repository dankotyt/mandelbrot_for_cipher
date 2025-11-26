package com.cipher.core.dto.encryption;

public record EncryptionArea(int startX, int startY, int width, int height) {
    public EncryptionArea {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }
    }
}
