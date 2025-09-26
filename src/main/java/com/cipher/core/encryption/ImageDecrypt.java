package com.cipher.core.encryption;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.awt.Graphics2D;

import com.cipher.core.dto.KeyDecoderParams;
import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.dto.neww.SegmentationParams;
import com.cipher.core.utils.BinaryFile;
import com.cipher.core.utils.DeterministicRandomGenerator;
import com.cipher.core.utils.ImageUtils;
import com.cipher.core.service.MandelbrotService;
import com.cipher.core.utils.Pair;
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

    private static String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }
    private static String getTempPath() {
        return getProjectRootPath() + "temp" + File.separator;
    }

    public void decryptImage(String keyFilePath) {
        try {
            // 1. Загружаем параметры дешифровки и зашифрованное изображение
            Pair<KeyDecoderParams, byte[]> result = binaryFile.loadKeyDecoderFromBinaryFile(keyFilePath);
            KeyDecoderParams keyDecoderParams = result.getKey();
            byte[] masterSeed = result.getValue();

            // Создаем DRBG для дешифрования
            drbg.initialize(masterSeed);

            BufferedImage encryptedImage = ImageIO.read(new File(getTempPath() + "input.png"));

            MandelbrotParams mandelbrotParams = keyDecoderParams.mandelbrotParams();
            SegmentationParams segmentationParams = keyDecoderParams.segmentationParams();

            double zoom = mandelbrotParams.zoom();
            double offsetX = mandelbrotParams.offsetX();
            double offsetY = mandelbrotParams.offsetY();
            int maxIter = mandelbrotParams.maxIter();
            int segmentSize = segmentationParams.segmentSize();
            Map<Integer, Integer> segmentMapping = segmentationParams.segmentMapping();
            int startX = keyDecoderParams.startX();
            int startY = keyDecoderParams.startY();
            byte[] encryptedMasterSeed = keyDecoderParams.encryptedMasterSeed();
            byte[] iv = keyDecoderParams.iv();
            byte[] salt = keyDecoderParams.salt();
            int width = segmentationParams.paddedWidth();
            int height = segmentationParams.paddedHeight();



            // Выделяем область для дешифровки (если работаем с частью изображения)
            BufferedImage encryptedArea = encryptedImage;
            if (width != encryptedImage.getWidth() || height != encryptedImage.getHeight()) {
                encryptedArea = encryptedImage.getSubimage(startX, startY, width, height);
            }

            // 2. Генерируем изображение Мандельброта с нужными параметрами и размерами
            MandelbrotService mandelbrotServiceGenerator = mandelbrotService.createWithSize(width, height);
            BufferedImage mandelbrotImage = mandelbrotServiceGenerator.generateImage(
                    width, height, zoom, offsetX, offsetY, maxIter);

            // 3. Применяем XOR между зашифрованным изображением и Мандельбротом
            mandelbrotImage = ImageUtils.convertToARGB(mandelbrotImage);
            encryptedArea = ImageUtils.convertToARGB(encryptedArea);

            BufferedImage xorResult = XOR.performXOR(encryptedArea, mandelbrotImage);

            // 4. Выполняем десегментацию (восстановление порядка сегментов)
            BufferedImage unshuffledImage = imageSegmentShuffler.unshuffledSegments(
                    xorResult, segmentMapping, segmentSize);

            // 5. Сохраняем или возвращаем результат
            if (width != encryptedImage.getWidth() || height != encryptedImage.getHeight()) {
                // Если работали с частью изображения, вставляем расшифрованную область обратно
                Graphics2D g2d = encryptedImage.createGraphics();
                g2d.drawImage(unshuffledImage, startX, startY, null);
                g2d.dispose();
                saveDecryptedImage(encryptedImage);
            } else {
                // Если работали со всем изображением
                saveDecryptedImage(unshuffledImage);
            }

        } catch (IOException e) {
            logger.error("Ошибка при дешифровании изображения: " + e.getMessage());
        }
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