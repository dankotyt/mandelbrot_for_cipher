package com.cipher.core.encryption;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.dto.encryption.EncryptedData;
import com.cipher.core.service.encryption.SessionMandelbrotGenerator;
import com.cipher.core.utils.EncryptionDataSerializer;
import com.cipher.core.service.encryption.MandelbrotService;
import com.cipher.core.utils.TempFileManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.cipher.core.encryption.XOR.performXOR;
import static com.cipher.core.encryption.XOR.xorBytes;

@Component
@RequiredArgsConstructor
public class ImageDecrypt {
    private final MandelbrotService mandelbrotService;
    private final SessionMandelbrotGenerator sessionGenerator;
    private final ImageSegmentShuffler imageSegmentShuffler;
    private final EncryptionDataSerializer serializer;
    private final TempFileManager tempFileManager;

    /**
     * Дешифрование из файла .bin
     */
    public BufferedImage decryptImage(File encryptedFile) throws Exception {
        if (!sessionGenerator.isInitialized()) {
            throw new IllegalStateException("Session generator not initialized");
        }

        // Загружаем данные из файла
        EncryptedData data = tempFileManager.loadEncryptedData(encryptedFile);

        // Дешифруем
        BufferedImage result = decryptImage(data);

        // Сохраняем результат в temp для предпросмотра
        tempFileManager.saveBufferedImageToTemp(result, "decrypted_image.png");

        return result;
    }

    /**
     * Дешифрование из EncryptedData
     */
    private BufferedImage decryptImage(EncryptedData data) throws Exception {
        // Получаем байты сессионного фрактала
        byte[] sessionFractalBytes = sessionGenerator.getSessionFractalBytes();

        // Расшифровываем параметры одноразового фрактала
        byte[] oneTimeParamsBytes = xorBytes(
                data.encryptedOneTimeParams(),
                sessionFractalBytes
        );
        MandelbrotParams oneTimeParams = mandelbrotService.bytesToParams(oneTimeParamsBytes);

        // Восстанавливаем изображение из байт
        BufferedImage encryptedImage = serializer.bytesToImage(data.encryptedImage());

        if (data.isPartial()) {
            return decryptPartial(encryptedImage, data, oneTimeParams);
        } else {
            return decryptWhole(encryptedImage, data, oneTimeParams);
        }
    }

    /**
     * Дешифрование полного изображения
     */
    private BufferedImage decryptWhole(
            BufferedImage encryptedImage,
            EncryptedData data,
            MandelbrotParams oneTimeParams
    ) {

        // Генерируем одноразовый фрактал
        BufferedImage oneTimeFractal = mandelbrotService.generateImage(
                encryptedImage.getWidth(),
                encryptedImage.getHeight(),
                oneTimeParams.zoom(),
                oneTimeParams.offsetX(),
                oneTimeParams.offsetY(),
                oneTimeParams.maxIter()
        );

        // XOR для получения перемешанного изображения
        BufferedImage shuffledImage = performXOR(encryptedImage, oneTimeFractal);

        // Восстанавливаем оригинальный порядок сегментов
        return imageSegmentShuffler.unshuffledSegments(
                shuffledImage,
                data.segmentMapping(),
                data.segmentSize()
        );
    }

    /**
     * Дешифрование части изображения
     */
    private BufferedImage decryptPartial(
            BufferedImage encryptedImage,
            EncryptedData data,
            MandelbrotParams oneTimeParams
    ) {

        // 1. Извлекаем зашифрованную область
        BufferedImage encryptedArea = encryptedImage.getSubimage(
                data.areaStartX(),
                data.areaStartY(),
                data.areaWidth(),
                data.areaHeight()
        );

        // 2. Генерируем одноразовый фрактал для размера области
        BufferedImage oneTimeFractal = mandelbrotService.generateImage(
                data.areaWidth(),
                data.areaHeight(),
                oneTimeParams.zoom(),
                oneTimeParams.offsetX(),
                oneTimeParams.offsetY(),
                oneTimeParams.maxIter()
        );

        // 3. XOR области с одноразовым фракталом
        BufferedImage shuffledArea = performXOR(encryptedArea, oneTimeFractal);

        // 4. Восстанавливаем сегменты в области
        BufferedImage decryptedArea = imageSegmentShuffler.unshuffledSegments(
                shuffledArea,
                data.segmentMapping(),
                data.segmentSize()
        );

        // 5. Создаем ПОЛНОЕ изображение
        BufferedImage result = new BufferedImage(
                encryptedImage.getWidth(),
                encryptedImage.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );

        // 6. Копируем полное изображение и заменяем область
        Graphics2D g = result.createGraphics();
        g.drawImage(encryptedImage, 0, 0, null);
        g.drawImage(decryptedArea, data.areaStartX(), data.areaStartY(), null);
        g.dispose();

        return result;
    }
}