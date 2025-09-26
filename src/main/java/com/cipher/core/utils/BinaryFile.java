package com.cipher.core.utils;

import com.cipher.core.dto.KeyDecoderParams;
import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.dto.neww.EncryptionDataResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.cipher.core.service.EncryptionService.getTempPath;

@Component
@RequiredArgsConstructor
public class BinaryFile {
    private static final Logger logger = LoggerFactory.getLogger(BinaryFile.class);
    private final DeterministicRandomGenerator drbg;

//    public static void saveMandelbrotParamsToBinaryFile(String filePath, MandelbrotParams params) {
//        try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(Paths.get(filePath)))) {
//            dos.writeInt(params.startMandelbrotWidth());
//            dos.writeInt(params.startMandelbrotHeight());
//            dos.writeDouble(params.zoom());
//            dos.writeDouble(params.offsetX());
//            dos.writeDouble(params.offsetY());
//            dos.writeInt(params.maxIter());
//            logger.info("Параметры сохранены в файл {}", filePath);
//        } catch (IOException e) {
//            logger.error("Ошибка при сохранении параметров", e);
//        }
//    }

    public void saveMandelbrotParamsToBinaryFile(String filePath, MandelbrotParams params) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {

            dos.writeInt(params.startMandelbrotWidth());
            dos.writeInt(params.startMandelbrotHeight());
            dos.writeDouble(params.zoom());
            dos.writeDouble(params.offsetX());
            dos.writeDouble(params.offsetY());
            dos.writeInt(params.maxIter());

            byte[] encryptedData = drbg.encryptData(baos.toByteArray());

            try (DataOutputStream fileDos = new DataOutputStream(Files.newOutputStream(Paths.get(filePath)))) {
                fileDos.writeInt(encryptedData.length);
                fileDos.write(encryptedData);
            }

            logger.info("Зашифрованные параметры сохранены в файл {}", filePath);
        } catch (Exception e) {
            logger.error("Ошибка при сохранении параметров", e);
        }
    }

//    public static MandelbrotParams loadMandelbrotParamsFromBinaryFile(String filePath) throws IOException {
//        try (DataInputStream dis = new DataInputStream(Files.newInputStream(Paths.get(filePath)))) {
//            return new MandelbrotParams(
//                    dis.readInt(),    // startMandelbrotWidth
//                    dis.readInt(),    // startMandelbrotHeight
//                    dis.readDouble(), // zoom
//                    dis.readDouble(), // offsetX
//                    dis.readDouble(), // offsetY
//                    dis.readInt()     // maxIter
//            );
//        } catch (IOException e) {
//            logger.error("Ошибка при загрузке параметров из файла {}", filePath, e);
//            throw e;
//        }
//    }

    public MandelbrotParams loadMandelbrotParamsFromBinaryFile(String filePath) throws IOException {
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(Paths.get(filePath)))) {
            int dataLength = dis.readInt();
            byte[] encryptedData = new byte[dataLength];
            dis.readFully(encryptedData);

            byte[] decryptedData = drbg.decryptData(encryptedData);

            try (DataInputStream dataDis = new DataInputStream(new ByteArrayInputStream(decryptedData))) {
                return new MandelbrotParams(
                        dataDis.readInt(),    // startMandelbrotWidth
                        dataDis.readInt(),    // startMandelbrotHeight
                        dataDis.readDouble(), // zoom
                        dataDis.readDouble(), // offsetX
                        dataDis.readDouble(), // offsetY
                        dataDis.readInt()     // maxIter
                );
            }
        } catch (Exception e) {
            logger.error("Ошибка при загрузке параметров из файла {}", filePath, e);
            throw new IOException("Failed to load encrypted parameters", e);
        }
    }

//    public static void saveKeyDecoderToBinaryFile(String filePath,
//                                                  KeyDecoderParams params) {
//        try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(Paths.get(filePath)))) {
//            dos.writeDouble(params.zoom());
//            dos.writeDouble(params.offsetX());
//            dos.writeDouble(params.offsetY());
//            dos.writeInt(params.maxIter());
//
//            dos.writeInt(params.segmentWidthSize());
//            dos.writeInt(params.segmentHeightSize());
//
//            Map<Integer, Integer> segmentMapping = params.segmentMapping();
//            dos.writeInt(segmentMapping.size());
//            for (Map.Entry<Integer, Integer> entry : segmentMapping.entrySet()) {
//                dos.writeInt(entry.getKey());
//                dos.writeInt(entry.getValue());
//            }
//
//            dos.writeInt(params.startX());
//            dos.writeInt(params.startY());
//            dos.writeInt(params.width());
//            dos.writeInt(params.height());
//
//            logger.info("Параметры сохранены в файл {}", filePath);
//            logParams(params);
//        } catch (IOException e) {
//            logger.error("Ошибка при сохранении параметров", e);
//        }
//    }

    public void saveKeyDecoderToBinaryFile(String filePath,
                                                  KeyDecoderParams params,
                                                  byte[] masterSeed) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {

            // Сначала записываем masterSeed
            dos.writeInt(masterSeed.length);
            dos.write(masterSeed);

            // Затем параметры
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

            byte[] encryptedData = drbg.encryptData(baos.toByteArray());

            // Сохраняем зашифрованные данные
            try (DataOutputStream fileDos = new DataOutputStream(Files.newOutputStream(Paths.get(filePath)))) {
                fileDos.writeInt(encryptedData.length);
                fileDos.write(encryptedData);
            }

            logger.info("Зашифрованные параметры с masterSeed сохранены в файл {}", filePath);
            logParams(params);
        } catch (Exception e) {
            logger.error("Ошибка при сохранении параметров", e);
        }
    }
//
//    public static KeyDecoderParams loadKeyDecoderFromBinaryFile(String filePath) {
//        try (DataInputStream dis = new DataInputStream(Files.newInputStream(Paths.get(filePath)))) {
//            double zoom = dis.readDouble();
//            double offsetX = dis.readDouble();
//            double offsetY = dis.readDouble();
//            int maxIter = dis.readInt();
//            int segmentWidthSize = dis.readInt();
//            int segmentHeightSize = dis.readInt();
//
//            int segmentCount = dis.readInt();
//            Map<Integer, Integer> segmentMapping = new HashMap<>();
//            for (int i = 0; i < segmentCount; i++) {
//                segmentMapping.put(dis.readInt(), dis.readInt());
//            }
//            KeyDecoderParams params = new KeyDecoderParams(
//                    zoom,
//                    offsetX,
//                    offsetY,
//                    maxIter,
//                    segmentWidthSize,
//                    segmentHeightSize,
//                    segmentMapping,
//                    dis.readInt(), // startX
//                    dis.readInt(), // startY
//                    dis.readInt(), // width
//                    dis.readInt()  // height
//            );
//            logParams(params);
//            return params;
//        } catch (IOException e) {
//            logger.error("Ошибка при загрузке параметров из файла {}", filePath, e);
//            throw new UncheckedIOException(e);
//        }
//    }

    public Pair<KeyDecoderParams, byte[]> loadKeyDecoderFromBinaryFile(String filePath) {
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(Paths.get(filePath)))) {
            int dataLength = dis.readInt();
            byte[] encryptedData = new byte[dataLength];
            dis.readFully(encryptedData);

            byte[] decryptedData = drbg.decryptData(encryptedData);

            try (DataInputStream dataDis = new DataInputStream(new ByteArrayInputStream(decryptedData))) {
                // Читаем masterSeed
                int seedLength = dataDis.readInt();
                byte[] masterSeed = new byte[seedLength];
                dataDis.readFully(masterSeed);

                // Используем настоящий masterSeed для дешифрования параметров
                drbg.initialize(masterSeed);
                byte[] realDecryptedData = drbg.decryptData(encryptedData);

                // Читаем параметры из правильно дешифрованных данных
                try (DataInputStream realDataDis = new DataInputStream(new ByteArrayInputStream(realDecryptedData))) {
                    // Пропускаем masterSeed (уже прочитали)
                    realDataDis.readInt();
                    byte[] skippedSeed = new byte[realDataDis.readInt()];
                    realDataDis.readFully(skippedSeed);

                    // Читаем параметры
                    double zoom = realDataDis.readDouble();
                    double offsetX = realDataDis.readDouble();
                    double offsetY = realDataDis.readDouble();
                    int maxIter = realDataDis.readInt();
                    int segmentWidthSize = realDataDis.readInt();
                    int segmentHeightSize = realDataDis.readInt();

                    int segmentCount = realDataDis.readInt();
                    Map<Integer, Integer> segmentMapping = new HashMap<>();
                    for (int i = 0; i < segmentCount; i++) {
                        segmentMapping.put(realDataDis.readInt(), realDataDis.readInt());
                    }

                    KeyDecoderParams params = new KeyDecoderParams(
                            zoom,
                            offsetX,
                            offsetY,
                            maxIter,
                            segmentWidthSize,
                            segmentHeightSize,
                            segmentMapping,
                            realDataDis.readInt(), // startX
                            realDataDis.readInt(), // startY
                            realDataDis.readInt(), // width
                            realDataDis.readInt()  // height
                    );

                    logParams(params);
                    return new Pair<>(params, masterSeed);
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при загрузке параметров из файла {}", filePath, e);
            throw new UncheckedIOException(new IOException("Failed to load encrypted parameters", e));
        }
    }

    private void saveEncryptedParams(EncryptionDataResult encryptedParams) {
        try {
            // Создаем структуру бинарного файла
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            // Записываем IV (12 bytes)
            dos.writeInt(encryptedParams.iv().length);
            dos.write(encryptedParams.iv());

            // Записываем Salt (16 bytes)
            dos.writeInt(encryptedParams.salt().length);
            dos.write(encryptedParams.salt());

            // Записываем зашифрованные данные
            dos.writeInt(encryptedParams.encryptedData().length);
            dos.write(encryptedParams.encryptedData());

            byte[] binaryData = baos.toByteArray();

            // Сохраняем в файл
            saveBinaryFile(
                    "encryption_params.bin",
                    binaryData
            );

        } catch (IOException e) {
            throw new RuntimeException("Failed to save encrypted parameters", e);
        }
    }

    private void saveBinaryFile(String filename, byte[] data) throws IOException {
        Path path = Paths.get(getTempPath(), filename);
        Files.createDirectories(path.getParent());
        Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public byte[] loadBinaryFile(String filename) throws IOException {
        Path path = Paths.get(getTempPath(), filename);
        return Files.readAllBytes(path);
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
