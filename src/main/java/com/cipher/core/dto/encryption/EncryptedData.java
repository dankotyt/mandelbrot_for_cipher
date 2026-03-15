package com.cipher.core.dto.encryption;

import java.util.Map;

/**
 * Единый DTO для зашифрованных данных
 */
public record EncryptedData(
        byte encryptionType,                    // 0x01 - полное, 0x02 - частичное
        byte[] encryptedImage,            // изображение после XOR
        byte[] encryptedOneTimeParams,           // параметры oneTime, XOR с session
        Map<Integer, Integer> segmentMapping,    // маппинг сегментов
        int segmentSize,                          // размер сегмента
        Integer areaStartX,                        // X начала области (null для полного)
        Integer areaStartY,                        // Y начала области (null для полного)
        Integer areaWidth,                          // ширина области (null для полного)
        Integer areaHeight,                         // высота области (null для полного)
        Integer originalWidth,                      // оригинальная ширина
        Integer originalHeight                       // оригинальная высота
) {

    public static final byte TYPE_WHOLE = 0x01;
    public static final byte TYPE_PARTIAL = 0x02;

    /**
     * Конструктор для полного шифрования
     */
    public static EncryptedData forWholeImage(
            byte[] encryptedImage,
            byte[] encryptedOneTimeParams,
            Map<Integer, Integer> segmentMapping,
            int segmentSize,
            int width, int height
    ) {
        return new EncryptedData(
                TYPE_WHOLE,
                encryptedImage,
                encryptedOneTimeParams,
                segmentMapping,
                segmentSize,
                null, null, null, null, width, height
        );
    }

    /**
     * Конструктор для частичного шифрования
     */
    public static EncryptedData forPartialImage(
            byte[] encryptedImage,
            byte[] encryptedOneTimeParams,
            Map<Integer, Integer> segmentMapping,
            int segmentSize,
            int areaStartX,
            int areaStartY,
            int areaWidth,
            int areaHeight,
            int originalWidth,
            int originalHeight
    ) {
        return new EncryptedData(
                TYPE_PARTIAL,
                encryptedImage,
                encryptedOneTimeParams,
                segmentMapping,
                segmentSize,
                areaStartX,
                areaStartY,
                areaWidth,
                areaHeight,
                originalWidth,
                originalHeight
        );
    }

    public boolean isPartial() {
        return encryptionType == TYPE_PARTIAL;
    }
}