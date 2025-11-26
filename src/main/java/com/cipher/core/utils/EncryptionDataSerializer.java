package com.cipher.core.utils;

import com.cipher.core.dto.encryption.EncryptionResult;
import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.dto.encryption.EncryptionArea;
import com.cipher.core.dto.encryption.EncryptionDataResult;
import com.cipher.core.dto.encryption.EncryptionParams;
import com.cipher.core.dto.segmentation.SegmentationParams;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
public class EncryptionDataSerializer {

    // Сериализация для сохранения в файл .bin
    public byte[] serializeForFile(EncryptionDataResult dataResult) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // 1. Зашифрованное изображение
        writeByteArray(dos, dataResult.encryptedImageData());

        // 2. Параметры шифрования
        writeEncryptionParams(dos, dataResult.params());

        // 3. Криптопараметры
        writeByteArray(dos, dataResult.iv());
        writeByteArray(dos, dataResult.salt());

        dos.flush();
        return baos.toByteArray();
    }

    // Десериализация из файла .bin
    public EncryptionDataResult deserializeFromFile(byte[] fileData) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(fileData);
        DataInputStream dis = new DataInputStream(bais);

        byte[] encryptedImageData = readByteArray(dis);
        EncryptionParams params = readEncryptionParams(dis);
        byte[] iv = readByteArray(dis);
        byte[] salt = readByteArray(dis);

        return new EncryptionDataResult(encryptedImageData, params, iv, salt);
    }

    // Сериализация изображения для внутреннего шифрования
    public byte[] serializeImage(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(image.getWidth());
        dos.writeInt(image.getHeight());
        dos.writeInt(image.getType());

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                dos.writeInt(image.getRGB(x, y));
            }
        }

        dos.flush();
        return baos.toByteArray();
    }

    // Десериализация изображения
    public BufferedImage deserializeImage(byte[] imageData) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
        DataInputStream dis = new DataInputStream(bais);

        int width = dis.readInt();
        int height = dis.readInt();
        int type = dis.readInt();

        BufferedImage image = new BufferedImage(width, height, type);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, dis.readInt());
            }
        }

        return image;
    }

    // Вспомогательные методы
    private void writeByteArray(DataOutputStream dos, byte[] data) throws IOException {
        dos.writeInt(data.length);
        if (data.length > 0) {
            dos.write(data);
        }
    }

    private byte[] readByteArray(DataInputStream dis) throws IOException {
        int length = dis.readInt();
        if (length == 0) return new byte[0];
        byte[] data = new byte[length];
        dis.readFully(data);
        return data;
    }

    private void writeEncryptionParams(DataOutputStream dos, EncryptionParams params) throws IOException {
        if (params == null) {
            dos.writeBoolean(false);
            return;
        }
        dos.writeBoolean(true);

        // MandelbrotParams
        writeMandelbrotParams(dos, params.mandelbrot());

        // SegmentationParams
        writeSegmentationParams(dos, params.segmentation());

        // EncryptionArea (может быть null)
        if (params.area() != null) {
            dos.writeBoolean(true);
            writeEncryptionArea(dos, params.area());
        } else {
            dos.writeBoolean(false);
        }
    }

    private EncryptionParams readEncryptionParams(DataInputStream dis) throws IOException {
        if (!dis.readBoolean()) return null;

        MandelbrotParams mandelbrot = readMandelbrotParams(dis);
        SegmentationParams segmentation = readSegmentationParams(dis);

        EncryptionArea area = null;
        if (dis.readBoolean()) {
            area = readEncryptionArea(dis);
        }

        return new EncryptionParams(area, segmentation, mandelbrot);
    }

    private void writeMandelbrotParams(DataOutputStream dos, MandelbrotParams params) throws IOException {
        dos.writeDouble(params.zoom());
        dos.writeDouble(params.offsetX());
        dos.writeDouble(params.offsetY());
        dos.writeInt(params.maxIter());
    }

    private MandelbrotParams readMandelbrotParams(DataInputStream dis) throws IOException {
        double zoom = dis.readDouble();
        double offsetX = dis.readDouble();
        double offsetY = dis.readDouble();
        int maxIter = dis.readInt();
        return new MandelbrotParams(zoom, offsetX, offsetY, maxIter);
    }

    private void writeSegmentationParams(DataOutputStream dos, SegmentationParams params) throws IOException {
        dos.writeInt(params.segmentSize());

        Map<Integer, Integer> mapping = params.segmentMapping();
        dos.writeInt(mapping.size());
        for (Map.Entry<Integer, Integer> entry : mapping.entrySet()) {
            dos.writeInt(entry.getKey());
            dos.writeInt(entry.getValue());
        }
    }

    private SegmentationParams readSegmentationParams(DataInputStream dis) throws IOException {
        int segmentSize = dis.readInt();

        int mapSize = dis.readInt();
        Map<Integer, Integer> segmentMapping = new HashMap<>();
        for (int i = 0; i < mapSize; i++) {
            int key = dis.readInt();
            int value = dis.readInt();
            segmentMapping.put(key, value);
        }

        return new SegmentationParams(segmentSize, segmentMapping);
    }

    private void writeEncryptionArea(DataOutputStream dos, EncryptionArea area) throws IOException {
        dos.writeInt(area.startX());
        dos.writeInt(area.startY());
        dos.writeInt(area.width());
        dos.writeInt(area.height());
    }

    private EncryptionArea readEncryptionArea(DataInputStream dis) throws IOException {
        int startX = dis.readInt();
        int startY = dis.readInt();
        int width = dis.readInt();
        int height = dis.readInt();
        return new EncryptionArea(startX, startY, width, height);
    }
}
