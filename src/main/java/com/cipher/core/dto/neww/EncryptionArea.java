package com.cipher.core.dto.neww;

public record EncryptionArea(int startX, int startY, int width, int height, boolean isWhole) {
    public EncryptionArea {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }
        if (!isWhole && (startX < 0 || startY < 0)) {
            throw new IllegalArgumentException("Start coordinates must be non-negative for partial encryption");
        }
    }

    public static EncryptionArea wholeImage(int width, int height) {
        return new EncryptionArea(0, 0, width, height, true);
    }
}
