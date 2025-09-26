package com.cipher.core.utils;

import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.dto.neww.EncryptionArea;
import com.cipher.core.dto.neww.EncryptionParams;
import com.cipher.core.dto.neww.SegmentationParams;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

@Component
public class EncryptionParamsSerializer {

    public byte[] serializeParams(EncryptionParams params) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        try {
            // EncryptionArea
            dos.writeBoolean(params.area().isWhole());
            dos.writeInt(params.area().startX());
            dos.writeInt(params.area().startY());
            dos.writeInt(params.area().width());
            dos.writeInt(params.area().height());

            // SegmentationParams
            dos.writeInt(params.segmentation().segmentSize());
            dos.writeInt(params.segmentation().paddedWidth());
            dos.writeInt(params.segmentation().paddedHeight());

            // Segment mapping
            Map<Integer, Integer> mapping = params.segmentation().segmentMapping();
            dos.writeInt(mapping.size());
            for (Map.Entry<Integer, Integer> entry : mapping.entrySet()) {
                dos.writeInt(entry.getKey());
                dos.writeInt(entry.getValue());
            }

            // Mandelbrot params
            dos.writeInt(params.mandelbrot().startMandelbrotWidth());
            dos.writeInt(params.mandelbrot().startMandelbrotHeight());
            dos.writeDouble(params.mandelbrot().zoom());
            dos.writeDouble(params.mandelbrot().offsetX());
            dos.writeDouble(params.mandelbrot().offsetY());
            dos.writeInt(params.mandelbrot().maxIter());

            // Master seed
            dos.writeInt(params.masterSeed().length);
            dos.write(params.masterSeed());

            return baos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Serialization error", e);
        }
    }

    public byte[] serializeSegmentationParams(SegmentationParams params) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        try {
            // SegmentationParams
            dos.writeInt(params.segmentSize());
            dos.writeInt(params.paddedWidth());
            dos.writeInt(params.paddedHeight());

            // Segment mapping
            Map<Integer, Integer> mapping = params.segmentMapping();
            dos.writeInt(mapping.size());
            for (Map.Entry<Integer, Integer> entry : mapping.entrySet()) {
                dos.writeInt(entry.getKey());
                dos.writeInt(entry.getValue());
            }

            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Serialization error", e);
        }
    }

    public EncryptionParams deserializeParams(byte[] data) {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);

        try {
            // EncryptionArea
            boolean isWhole = dis.readBoolean();
            int startX = dis.readInt();
            int startY = dis.readInt();
            int width = dis.readInt();
            int height = dis.readInt();
            EncryptionArea area = new EncryptionArea(startX, startY, width, height, isWhole);

            // SegmentationParams
            int segmentSize = dis.readInt();
            int paddedWidth = dis.readInt();
            int paddedHeight = dis.readInt();

            // Segment mapping
            int mappingSize = dis.readInt();
            Map<Integer, Integer> segmentMapping = new HashMap<>();
            for (int i = 0; i < mappingSize; i++) {
                int key = dis.readInt();
                int value = dis.readInt();
                segmentMapping.put(key, value);
            }
            SegmentationParams segmentation = new SegmentationParams(segmentSize, paddedWidth, paddedHeight, segmentMapping);

            // Mandelbrot params
            int mandelbrotWidth = dis.readInt();
            int mandelbrotHeight = dis.readInt();
            double zoom = dis.readDouble();
            double offsetX = dis.readDouble();
            double offsetY = dis.readDouble();
            int maxIter = dis.readInt();
            MandelbrotParams mandelbrot = new MandelbrotParams(mandelbrotWidth, mandelbrotHeight, zoom, offsetX, offsetY, maxIter);

            // Master seed
            int seedLength = dis.readInt();
            byte[] masterSeed = new byte[seedLength];
            dis.readFully(masterSeed);

            return new EncryptionParams(area, segmentation, mandelbrot, masterSeed);

        } catch (IOException e) {
            throw new RuntimeException("Deserialization error", e);
        }
    }
}
