package com.cipher.core.utils;

import com.cipher.core.dto.encryption.EncryptedData;
import org.springframework.stereotype.Component;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

@Component
public class EncryptionDataSerializer {

    /**
     * Сериализует зашифрованные данные в массив байт
     */
    public byte[] serialize(EncryptedData data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {

            // 1. Тип шифрования
            dos.writeByte(data.encryptionType());

            // 2. Зашифрованное изображение
            writeByteArray(dos, data.encryptedImage());

            // 3. Зашифрованные параметры oneTime фрактала
            writeByteArray(dos, data.encryptedOneTimeParams());

            // 4. Параметры сегментации
            dos.writeInt(data.segmentSize());
            Map<Integer, Integer> mapping = data.segmentMapping();
            dos.writeInt(mapping.size());
            for (Map.Entry<Integer, Integer> entry : mapping.entrySet()) {
                dos.writeInt(entry.getKey());
                dos.writeInt(entry.getValue());
            }

            // 5. Для частичного шифрования - информация об области
            if (data.isPartial()) {
                dos.writeInt(data.areaStartX());
                dos.writeInt(data.areaStartY());
                dos.writeInt(data.areaWidth());
                dos.writeInt(data.areaHeight());
            }

            // 6. Размеры изображения
            dos.writeInt(data.originalWidth());
            dos.writeInt(data.originalHeight());

            dos.flush();
        }
        return baos.toByteArray();
    }

    /**
     * Десериализует зашифрованные данные из массива байт
     */
    public EncryptedData deserialize(byte[] fileData) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(fileData);
        try (DataInputStream dis = new DataInputStream(bais)) {

            // 1. Тип шифрования
            byte type = dis.readByte();

            // 2. Зашифрованное изображение
            byte[] encryptedImage = readByteArray(dis);

            // 3. Зашифрованные параметры
            byte[] encryptedOneTimeParams = readByteArray(dis);

            // 4. Параметры сегментации
            int segmentSize = dis.readInt();
            int mapSize = dis.readInt();
            Map<Integer, Integer> segmentMapping = new HashMap<>();
            for (int i = 0; i < mapSize; i++) {
                segmentMapping.put(dis.readInt(), dis.readInt());
            }

            // 5. Для частичного шифрования - информация об области
            Integer areaStartX = null;
            Integer areaStartY = null;
            Integer areaWidth = null;
            Integer areaHeight = null;

            if (type == EncryptedData.TYPE_PARTIAL) {
                areaStartX = dis.readInt();
                areaStartY = dis.readInt();
                areaWidth = dis.readInt();
                areaHeight = dis.readInt();
            }

            // 6. Размеры изображения
            int imageWidth = dis.readInt();
            int imageHeight = dis.readInt();

            if (type == EncryptedData.TYPE_PARTIAL) {
                return EncryptedData.forPartialImage(
                        encryptedImage,
                        encryptedOneTimeParams,
                        segmentMapping,
                        segmentSize,
                        areaStartX, areaStartY,
                        areaWidth, areaHeight,
                        imageWidth, imageHeight
                );
            } else {
                return EncryptedData.forWholeImage(
                        encryptedImage,
                        encryptedOneTimeParams,
                        segmentMapping,
                        segmentSize,
                        imageWidth, imageHeight
                );
            }
        }
    }

    /**
     * Конвертирует BufferedImage в массив байт
     */
    public byte[] imageToBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    dos.writeInt(image.getRGB(x, y));
                }
            }
        }
        return baos.toByteArray();
    }

    /**
     * Конвертирует массив байт в BufferedImage
     */
    public BufferedImage bytesToImage(byte[] bytes) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        try (DataInputStream dis = new DataInputStream(bais)) {
            int width = dis.readInt();
            int height = dis.readInt();
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setRGB(x, y, dis.readInt());
                }
            }
            return image;
        }
    }

    /**
     * Записывает массив байт с префиксом длины
     */
    private void writeByteArray(DataOutputStream dos, byte[] data) throws IOException {
        dos.writeInt(data.length);
        if (data.length > 0) {
            dos.write(data);
        }
    }

    /**
     * Читает массив байт с префиксом длины
     */
    private byte[] readByteArray(DataInputStream dis) throws IOException {
        int length = dis.readInt();
        if (length == 0) return new byte[0];
        byte[] data = new byte[length];
        dis.readFully(data);
        return data;
    }
}