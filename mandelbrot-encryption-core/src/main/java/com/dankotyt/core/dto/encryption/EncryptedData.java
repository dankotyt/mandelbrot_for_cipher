package com.dankotyt.core.dto.encryption;

/**
 * DTO для зашифрованных данных
 */
public record EncryptedData(byte[] sessionSalt, int attemptCount, int startX, int startY,
                            int areaWidth, int areaHeight, int originalWidth, int originalHeight,
                            byte[] imageBytes) {}