package com.cipher.core.utils;

import com.cipher.core.dto.EncryptionResult;
import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.dto.neww.EncryptionArea;
import com.cipher.core.dto.neww.EncryptionParams;
import com.cipher.core.dto.neww.SegmentationParams;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class EncryptionDataSerializer {
    private final int processors;

    public EncryptionDataSerializer() {
        this.processors = Runtime.getRuntime().availableProcessors();
    }

    public EncryptionDataSerializer(int threadCount) {
        this.processors = threadCount;
    }

    public byte[] serialize(EncryptionResult result) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        byte[] segmentedImageData = serializeImageParallel(result.segmentedImage());
        byte[] fractalPreviewData = serializeImageParallel(result.fractalImage());

        writeByteArray(dos, segmentedImageData);
        writeByteArray(dos, fractalPreviewData);
        writeEncryptionParams(dos, result.params());

        dos.flush();
        return baos.toByteArray();
    }

    private byte[] serializeImageParallel(BufferedImage image) throws IOException {
        if (image == null) {
            return new byte[0];
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int type = image.getType();

        if (width * height < 100000) {
            return serializeImageSingleThread(image);
        }

        try (ExecutorService executor = Executors.newFixedThreadPool(processors)) {
            int chunkHeight = height / processors;
            List<Future<byte[]>> futures = new ArrayList<>();
            List<byte[]> results = new ArrayList<>();

            for (int i = 0; i < processors; i++) {
                int startY = i * chunkHeight;
                int actualHeight = (i == processors - 1) ? height - startY : chunkHeight;

                futures.add(executor.submit(() ->
                        serializeImageRegion(image, 0, startY, width, actualHeight)
                ));
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeInt(width);
            dos.writeInt(height);
            dos.writeInt(type);

            for (Future<byte[]> future : futures) {
                results.add(future.get());
            }

            for (byte[] chunk : results) {
                dos.write(chunk);
            }

            dos.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IOException("Parallel serialization failed", e);
        }
    }

    // Сериализация региона изображения
    private byte[] serializeImageRegion(BufferedImage image, int startX, int startY, int width, int height)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        for (int y = startY; y < startY + height; y++) {
            for (int x = startX; x < startX + width; x++) {
                dos.writeInt(image.getRGB(x, y));
            }
        }

        dos.flush();
        return baos.toByteArray();
    }

    // Однопоточная сериализация для маленьких изображений
    private byte[] serializeImageSingleThread(BufferedImage image) throws IOException {
        if (image == null) return new byte[0];

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

    public EncryptionResult deserialize(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);

        byte[] segmentedImageData = readByteArray(dis);
        byte[] fractalPreviewData = readByteArray(dis);

        BufferedImage segmentedImage = deserializeImageSingleThread(segmentedImageData);
        BufferedImage fractalPreview = deserializeImageSingleThread(fractalPreviewData);
        EncryptionParams params = readEncryptionParams(dis);

        return new EncryptionResult(segmentedImage, fractalPreview, params);
    }

    private BufferedImage deserializeImageSingleThread(byte[] imageData) throws IOException {
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

        writeEncryptionArea(dos, params.area());
        writeSegmentationParams(dos, params.segmentation());
        writeMandelbrotParams(dos, params.mandelbrot());
    }

    private EncryptionParams readEncryptionParams(DataInputStream dis) throws IOException {
        if (!dis.readBoolean()) return null;

        EncryptionArea area = readEncryptionArea(dis);
        SegmentationParams segmentation = readSegmentationParams(dis);
        MandelbrotParams mandelbrot = readMandelbrotParams(dis);

        return new EncryptionParams(area, segmentation, mandelbrot);
    }

    private void writeEncryptionArea(DataOutputStream dos, EncryptionArea area) throws IOException {
        if (area == null) {
            dos.writeBoolean(false);
            return;
        }
        dos.writeBoolean(true);

        dos.writeInt(area.startX());
        dos.writeInt(area.startY());
        dos.writeInt(area.width());
        dos.writeInt(area.height());
        dos.writeBoolean(area.isWhole());
    }

    private EncryptionArea readEncryptionArea(DataInputStream dis) throws IOException {
        if (!dis.readBoolean()) return null;

        int startX = dis.readInt();
        int startY = dis.readInt();
        int width = dis.readInt();
        int height = dis.readInt();
        boolean isWhole = dis.readBoolean();

        return new EncryptionArea(startX, startY, width, height, isWhole);
    }

    private void writeSegmentationParams(DataOutputStream dos, SegmentationParams params) throws IOException {
        if (params == null) {
            dos.writeBoolean(false);
            return;
        }
        dos.writeBoolean(true);

        dos.writeInt(params.segmentSize());
        dos.writeInt(params.paddedWidth());
        dos.writeInt(params.paddedHeight());

        Map<Integer, Integer> mapping = params.segmentMapping();
        dos.writeInt(mapping.size());
        for (Map.Entry<Integer, Integer> entry : mapping.entrySet()) {
            dos.writeInt(entry.getKey());
            dos.writeInt(entry.getValue());
        }
    }

    private SegmentationParams readSegmentationParams(DataInputStream dis) throws IOException {
        if (!dis.readBoolean()) return null;

        int segmentSize = dis.readInt();
        int paddedWidth = dis.readInt();
        int paddedHeight = dis.readInt();

        int mapSize = dis.readInt();
        Map<Integer, Integer> segmentMapping = new java.util.HashMap<>();
        for (int i = 0; i < mapSize; i++) {
            int key = dis.readInt();
            int value = dis.readInt();
            segmentMapping.put(key, value);
        }

        return new SegmentationParams(segmentSize, paddedWidth, paddedHeight, segmentMapping);
    }

    private void writeMandelbrotParams(DataOutputStream dos, MandelbrotParams params) throws IOException {
        if (params == null) {
            dos.writeBoolean(false);
            return;
        }
        dos.writeBoolean(true);

        dos.writeInt(params.startMandelbrotWidth());
        dos.writeInt(params.startMandelbrotHeight());
        dos.writeDouble(params.zoom());
        dos.writeDouble(params.offsetX());
        dos.writeDouble(params.offsetY());
        dos.writeInt(params.maxIter());
    }

    private MandelbrotParams readMandelbrotParams(DataInputStream dis) throws IOException {
        if (!dis.readBoolean()) return null;

        int startWidth = dis.readInt();
        int startHeight = dis.readInt();
        double zoom = dis.readDouble();
        double offsetX = dis.readDouble();
        double offsetY = dis.readDouble();
        int maxIter = dis.readInt();

        return new MandelbrotParams(startWidth, startHeight, zoom, offsetX, offsetY, maxIter);
    }
}
