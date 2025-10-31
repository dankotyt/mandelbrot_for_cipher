package com.cipher.core.encryption;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.Map;

import com.cipher.common.exception.CheckingException;
import com.cipher.core.dto.EncryptionResult;
import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.dto.neww.EncryptionDataResult;
import com.cipher.core.dto.neww.EncryptionParams;
import com.cipher.core.dto.neww.SegmentationParams;
import com.cipher.core.service.KeyExchangeService;
import com.cipher.core.utils.BinaryFile;
import com.cipher.core.utils.DeterministicRandomGenerator;
import com.cipher.core.utils.EncryptionDataSerializer;
import com.cipher.core.utils.ImageUtils;
import com.cipher.core.service.MandelbrotService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageDecrypt {
    private static final Logger logger = LoggerFactory.getLogger(ImageDecrypt.class);

    private final MandelbrotService mandelbrotService;
    private final DeterministicRandomGenerator drbg;
    private final BinaryFile binaryFile;
    private final ImageSegmentShuffler imageSegmentShuffler;
    private final EncryptionDataSerializer serializer;
    private final CryptographicService cryptographicService;
    private final KeyExchangeService keyExchangeService;

    private static String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }
    private static String getTempPath() {
        return getProjectRootPath() + "temp" + File.separator;
    }

    public BufferedImage decryptImage(File encryptedFile, InetAddress peerAddress) throws Exception {
        try {
            // Проверяем соединение с пиром
            if (!keyExchangeService.isConnectedTo(peerAddress)) {
                throw new IllegalStateException("Not connected to peer: " + peerAddress.getHostAddress());
            }

            byte[] fileData = Files.readAllBytes(encryptedFile.toPath());
            EncryptionDataResult encryptedDataResult = serializer.deserializeEncryptionDataResult(fileData);

            // Дешифруем данные, передавая peerAddress
            EncryptionResult result = cryptographicService.decryptData(encryptedDataResult, peerAddress);

            BufferedImage encryptedImage = result.segmentedImage();
            BufferedImage fractalImage = result.fractalImage();
            EncryptionParams params = result.params();

            MandelbrotParams mandelbrotParams = params.mandelbrot();

            BufferedImage genFractal = mandelbrotService.generateImage(
                    mandelbrotParams.startMandelbrotWidth(),
                    mandelbrotParams.startMandelbrotHeight(),
                    mandelbrotParams.zoom(),
                    mandelbrotParams.offsetX(),
                    mandelbrotParams.offsetY(),
                    mandelbrotParams.maxIter());

            if (!compareImages(genFractal, fractalImage)) {
                throw new CheckingException("Сгенерированный и используемый фракталы отличаются! Возможна подмена!");
            }

            BufferedImage segmentedImage = XOR.performXOR(encryptedImage, genFractal);

            SegmentationParams segmentationParams = params.segmentation();
            Map<Integer, Integer> segmentMapping = segmentationParams.segmentMapping();

            return imageSegmentShuffler.unshuffledSegments(
                    segmentedImage,
                    segmentMapping,
                    segmentationParams.segmentSize()
            );
        } catch (Exception e) {
            logger.error("Ошибка при дешифровке изображения от {}: {}",
                    peerAddress.getHostAddress(), e.getMessage(), e);
            throw new Exception("Не удалось дешифровать изображение: " + e.getMessage(), e);
        }
    }

    // Метод для проверки возможности дешифрования
    public boolean canDecryptFromPeer(InetAddress peerAddress) {
        return keyExchangeService.isConnectedTo(peerAddress);
    }

    private boolean compareImages(BufferedImage img1, BufferedImage img2) {
        if (img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight()) {
            return false;
        }

        DataBuffer db1 = img1.getRaster().getDataBuffer();
        DataBuffer db2 = img2.getRaster().getDataBuffer();

        if (db1.getSize() != db2.getSize()) {
            return false;
        }

        for (int i = 0; i < db1.getSize(); i++) {
            if (db1.getElem(i) != db2.getElem(i)) {
                return false;
            }
        }
        return true;
    }

    protected static void saveDecryptedImage(BufferedImage decryptedImage) {
        try {
            ImageIO.write(decryptedImage, "png", new File(getTempPath() + "decrypted_image.png"));
            logger.info("Дешифрованное изображение сохранено как decrypted_image.png");
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }
}