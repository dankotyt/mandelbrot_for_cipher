package com.cipher.core.utils;

import com.cipher.core.dto.KeyDecoderParams;
import com.cipher.core.dto.MandelbrotParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class BinaryFile {
    private static final Logger logger = LoggerFactory.getLogger(BinaryFile.class);

    public static void saveMandelbrotParamsToBinaryFile(String filePath, MandelbrotParams params) {
        try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(Paths.get(filePath)))) {
            dos.writeInt(params.startMandelbrotWidth());
            dos.writeInt(params.startMandelbrotHeight());
            dos.writeDouble(params.zoom());
            dos.writeDouble(params.offsetX());
            dos.writeDouble(params.offsetY());
            dos.writeInt(params.maxIter());
            logger.info("Параметры сохранены в файл {}", filePath);
        } catch (IOException e) {
            logger.error("Ошибка при сохранении параметров", e);
        }
    }

    public static MandelbrotParams loadMandelbrotParamsFromBinaryFile(String filePath) throws IOException {
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(Paths.get(filePath)))) {
            return new MandelbrotParams(
                    dis.readInt(),    // startMandelbrotWidth
                    dis.readInt(),    // startMandelbrotHeight
                    dis.readDouble(), // zoom
                    dis.readDouble(), // offsetX
                    dis.readDouble(), // offsetY
                    dis.readInt()     // maxIter
            );
        } catch (IOException e) {
            logger.error("Ошибка при загрузке параметров из файла {}", filePath, e);
            throw e;
        }
    }

    public static void saveKeyDecoderToBinaryFile(String filePath,
                                                  KeyDecoderParams params) {
        try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(Paths.get(filePath)))) {
            dos.writeDouble(params.zoom());
            dos.writeDouble(params.offsetX());
            dos.writeDouble(params.offsetY());
            dos.writeInt(params.maxIter());

            dos.writeInt(params.segmentWidthSize());
            dos.writeInt(params.segmentHeightSize());

            Map<Integer, Integer> segmentMapping = params.segmentMapping();
            dos.writeInt(segmentMapping.size());
            for (Map.Entry<Integer, Integer> entry : segmentMapping.entrySet()) {
                dos.writeInt(entry.getKey());
                dos.writeInt(entry.getValue());
            }

            dos.writeInt(params.startX());
            dos.writeInt(params.startY());
            dos.writeInt(params.width());
            dos.writeInt(params.height());

            logger.info("Параметры сохранены в файл {}", filePath);
            logParams(params);
        } catch (IOException e) {
            logger.error("Ошибка при сохранении параметров", e);
        }
    }

    public static KeyDecoderParams loadKeyDecoderFromBinaryFile(String filePath) {
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(Paths.get(filePath)))) {
            double zoom = dis.readDouble();
            double offsetX = dis.readDouble();
            double offsetY = dis.readDouble();
            int maxIter = dis.readInt();
            int segmentWidthSize = dis.readInt();
            int segmentHeightSize = dis.readInt();

            int segmentCount = dis.readInt();
            Map<Integer, Integer> segmentMapping = new HashMap<>();
            for (int i = 0; i < segmentCount; i++) {
                segmentMapping.put(dis.readInt(), dis.readInt());
            }
            KeyDecoderParams params = new KeyDecoderParams(
                    zoom,
                    offsetX,
                    offsetY,
                    maxIter,
                    segmentWidthSize,
                    segmentHeightSize,
                    segmentMapping,
                    dis.readInt(), // startX
                    dis.readInt(), // startY
                    dis.readInt(), // width
                    dis.readInt()  // height
            );
            logParams(params);
            return params;
        } catch (IOException e) {
            logger.error("Ошибка при загрузке параметров из файла {}", filePath, e);
            throw new UncheckedIOException(e);
        }
    }

    private static void logParams(KeyDecoderParams params) {
        logger.info("=== Параметры Мандельброта ===");
        logger.info("Zoom: {}", params.zoom());
        logger.info("Offset X: {}", params.offsetX());
        logger.info("Offset Y: {}", params.offsetY());
        logger.info("Max iterations: {}", params.maxIter());

        logger.info("=== Параметры декодера ===");
        logger.info("Segment width: {}", params.segmentWidthSize());
        logger.info("Segment height: {}", params.segmentHeightSize());
        logger.info("Segments count: {}", params.segmentMapping().size());
        logger.info("Start X: {}", params.startX());
        logger.info("Start Y: {}", params.startY());
        logger.info("Width: {}", params.width());
        logger.info("Height: {}", params.height());
    }
}
