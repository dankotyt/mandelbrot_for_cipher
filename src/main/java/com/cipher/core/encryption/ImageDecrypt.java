package com.cipher.core.encryption;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.awt.Graphics2D;

import com.cipher.core.dto.KeyDecoderParams;
import com.cipher.core.utils.BinaryFile;
import com.cipher.core.utils.ImageUtils;
import com.cipher.core.service.MandelbrotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ImageDecrypt {
    private static final Logger logger = LoggerFactory.getLogger(ImageDecrypt.class);

    private static String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }

    private static String getTempPath() {
        return getProjectRootPath() + "temp" + File.separator;
    }

    public static void decryptImage(String keyFilePath) {
        try {
            // 1. Загружаем параметры дешифровки и зашифрованное изображение
            KeyDecoderParams keyDecoderParams = BinaryFile.loadKeyDecoderFromBinaryFile(keyFilePath);
            BufferedImage encryptedImage = ImageIO.read(new File(getTempPath() + "input.png"));

            double zoom = keyDecoderParams.zoom();
            double offsetX = keyDecoderParams.offsetX();
            double offsetY = keyDecoderParams.offsetY();
            int maxIter = keyDecoderParams.maxIter();
            int segmentWidthSize = keyDecoderParams.segmentWidthSize();
            int segmentHeightSize = keyDecoderParams.segmentHeightSize();
            Map<Integer, Integer> segmentMapping = keyDecoderParams.segmentMapping();
            int startX = keyDecoderParams.startX();
            int startY = keyDecoderParams.startY();
            int width = keyDecoderParams.width();
            int height = keyDecoderParams.height();

            // Выделяем область для дешифровки (если работаем с частью изображения)
            BufferedImage encryptedArea = encryptedImage;
            if (width != encryptedImage.getWidth() || height != encryptedImage.getHeight()) {
                encryptedArea = encryptedImage.getSubimage(startX, startY, width, height);
            }

            // 2. Генерируем изображение Мандельброта с нужными параметрами и размерами
            MandelbrotService mandelbrotServiceGenerator = new MandelbrotService(width, height);
            BufferedImage mandelbrotImage = mandelbrotServiceGenerator.generateImage(
                    width, height, zoom, offsetX, offsetY, maxIter);

            // 3. Применяем XOR между зашифрованным изображением и Мандельбротом
            // Преобразование типов изображений в BufferedImage.TYPE_INT_RGB
            mandelbrotImage = ImageUtils.convertToType(mandelbrotImage, BufferedImage.TYPE_INT_RGB);
            encryptedArea = ImageUtils.convertToType(encryptedArea, BufferedImage.TYPE_INT_RGB);

            BufferedImage xorResult = XOR.performXOR(encryptedArea, mandelbrotImage);

            // 4. Выполняем десегментацию (восстановление порядка сегментов)
            BufferedImage unshuffledImage = ImageSegmentShuffler.unshuffledSegments(
                    xorResult, segmentMapping, segmentWidthSize, segmentHeightSize);

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