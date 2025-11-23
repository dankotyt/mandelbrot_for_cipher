package com.cipher.core;

import com.cipher.core.encryption.CryptographicService;
import com.cipher.core.encryption.ImageEncrypt;
import com.cipher.core.encryption.ImageSegmentShuffler;
import com.cipher.core.service.encryption.MandelbrotService;
import com.cipher.core.service.network.ConnectionManager;
import com.cipher.core.service.network.KeyExchangeService;
import com.cipher.core.utils.BinaryFile;
import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.ImageUtils;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class TestEncryptionRunner {
    private static final Logger logger = LoggerFactory.getLogger(TestEncryptionRunner.class);

    private final ImageEncrypt imageEncrypt;
    private final MandelbrotService mandelbrotService;
    private final ImageSegmentShuffler imageSegmentShuffler;
    private final CryptographicService cryptographicService;
    private final KeyExchangeService keyExchangeService;
    private final ConnectionManager connectionManager;
    private final DialogDisplayer dialogDisplayer;
    private final BinaryFile binaryFile;
    private final ImageUtils imageUtils;

    private static final int TOTAL_ITERATIONS = 1000;
    private String outputDirectory;
    private BufferedImage originalImage;

    /**
     * Запуск тестирования 1000 генераций
     */
    public void runEncryptionTest(Stage primaryStage) {
        try {
            // 1. Выбор исходного изображения через диалог
            selectOriginalImageFile(primaryStage);
            if (originalImage == null) {
                logger.error("Изображение не выбрано, тестирование прервано");
                return;
            }

            // 2. Выбор папки для сохранения через диалог
            selectOutputDirectory(primaryStage);
            if (outputDirectory == null) {
                logger.error("Папка для сохранения не выбрана, тестирование прервано");
                return;
            }

            // 3. Проверка подключения к пиру
            if (!checkPeerConnection()) {
                logger.error("Нет подключения к пиру, тестирование прервано");
                return;
            }

            // 4. Запуск тестирования
            runTestIterations();

        } catch (Exception e) {
            logger.error("Критическая ошибка при тестировании: {}", e.getMessage(), e);
        }
    }

    /**
     * Выбор исходного изображения через диалог
     */
    private void selectOriginalImageFile(Stage primaryStage) {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Выберите изображение для тестирования");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg")
            );

            File selectedFile = fileChooser.showOpenDialog(primaryStage);

            if (selectedFile != null) {
                originalImage = ImageIO.read(selectedFile);
                logger.info("✅ Загружено исходное изображение: {} ({}x{})",
                        selectedFile.getAbsolutePath(), originalImage.getWidth(), originalImage.getHeight());
            } else {
                logger.info("Файл не выбран.");
            }
        } catch (IOException e) {
            logger.error("Ошибка загрузки изображения: {}", e.getMessage());
            dialogDisplayer.showErrorDialog("Ошибка загрузки изображения: " + e.getMessage());
        }
    }

    /**
     * Выбор папки для сохранения через диалог
     */
    private void selectOutputDirectory(Stage primaryStage) {
        try {
            javafx.stage.DirectoryChooser directoryChooser = new javafx.stage.DirectoryChooser();
            directoryChooser.setTitle("Выберите папку для сохранения результатов теста");

            File selectedDirectory = directoryChooser.showDialog(primaryStage);

            if (selectedDirectory != null) {
                // Создаем подпапку с timestamp для уникальности
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                outputDirectory = selectedDirectory.getAbsolutePath() + File.separator + "encryption_test_" + timestamp + File.separator;

                Path path = Paths.get(outputDirectory);
                Files.createDirectories(path);

                logger.info("✅ Папка для сохранения создана: {}", outputDirectory);
            }
        } catch (IOException e) {
            logger.error("Ошибка создания папки для сохранения: {}", e.getMessage());
            dialogDisplayer.showErrorDialog("Ошибка создания папки: " + e.getMessage());
        }
    }

    /**
     * Проверка подключения к пиру
     */
    private boolean checkPeerConnection() {
        try {
            var peerAddress = connectionManager.getConnectedPeer();
            if (peerAddress == null) {
                dialogDisplayer.showErrorDialog("❌ Нет подключенного пира. Сначала установите соединение.");
                return false;
            }

            boolean hasKeys = keyExchangeService.hasKeysForPeer(peerAddress.getHostAddress());
            if (!hasKeys) {
                dialogDisplayer.showErrorDialog("❌ Нет ключей для пира: " + peerAddress.getHostAddress());
                return false;
            }

            logger.info("✅ Подключение к пиру установлено: {}", peerAddress.getHostAddress());
            return true;

        } catch (Exception e) {
            logger.error("Ошибка проверки подключения к пиру: {}", e.getMessage());
            dialogDisplayer.showErrorDialog("Ошибка проверки подключения: " + e.getMessage());
            return false;
        }
    }

    /**
     * Запуск 1000 итераций тестирования
     */
    private void runTestIterations() {
        logger.info("🚀 Начало тестирования {} итераций...", TOTAL_ITERATIONS);

        long startTime = System.currentTimeMillis();
        int successCount = 0;
        int failCount = 0;

        for (int i = 1; i <= TOTAL_ITERATIONS; i++) {
            try {
                logger.info("🔄 Итерация {}/{}", i, TOTAL_ITERATIONS);

                // Генерируем новые ключи для каждой итерации
                generateNewKeys();

                // Выполняем шифрование
                boolean success = performEncryptionIteration(i);

                if (success) {
                    successCount++;
                    logger.info("✅ Итерация {} завершена успешно", i);
                } else {
                    failCount++;
                    logger.warn("❌ Итерация {} завершена с ошибкой", i);
                }

                // Прогресс каждые 10%
                if (i % (TOTAL_ITERATIONS / 10) == 0) {
                    logProgress(i, successCount, failCount);
                }

            } catch (Exception e) {
                failCount++;
                logger.error("❌ Критическая ошибка в итерации {}: {}", i, e.getMessage());
            }
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        logFinalResults(successCount, failCount, totalTime);
        showFinalDialog(successCount, failCount, totalTime);
    }

    /**
     * Генерация новых ключей для итерации
     */
    private void generateNewKeys() {
        try {
            var peerAddress = connectionManager.getConnectedPeer();
            if (peerAddress != null) {
                keyExchangeService.generateNewKeys();
                boolean keyExchangeSuccess = keyExchangeService.performKeyExchange(peerAddress);

                if (keyExchangeSuccess) {
                    logger.debug("🔄 Новые ключи сгенерированы для итерации");
                } else {
                    logger.warn("⚠️ Не удалось обновить ключи для итерации");
                }
            }
        } catch (Exception e) {
            logger.warn("Ошибка генерации новых ключей: {}", e.getMessage());
        }
    }

    /**
     * Выполнение одной итерации шифрования
     */
    private boolean performEncryptionIteration(int iteration) {
        try {
            // Выполняем шифрование
            TestEncryptionResult result = encryptImageForTest();

            if (result != null) {
                // Сохраняем результаты с номером итерации
                saveIterationResults(result, iteration);
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.error("Ошибка в итерации {}: {}", iteration, e.getMessage());
            return false;
        }
    }

    /**
     * Шифрование изображения для теста
     */
    private TestEncryptionResult encryptImageForTest() {
        try {
            // ПОЛУЧАЕМ ПИРА И МАСТЕР-СИД ПЕРЕД СЕГМЕНТАЦИЕЙ
            var peerAddress = connectionManager.getConnectedPeer();
            if (peerAddress == null) {
                throw new IllegalStateException("Peer address is null");
            }

            // Получаем мастер-сид из DH обмена
            byte[] masterSeed = keyExchangeService.getMasterSeedFromDH(peerAddress);
            if (masterSeed == null) {
                throw new IllegalStateException("Master seed is null for peer: " + peerAddress.getHostAddress());
            }

            logger.debug("✅ Получен мастер-сид для итерации: {} байт", masterSeed.length);

            // ИНИЦИАЛИЗИРУЕМ СЕГМЕНТАТОР МАСТЕР-СИДОМ
            imageSegmentShuffler.initializeWithSeed(masterSeed);

            // Сегментация и перемешивание
            var segmentationResult = imageSegmentShuffler.segmentAndShuffle(originalImage);
            BufferedImage segmentedImage = segmentationResult.shuffledImage();

            // Генерация фрактала
            BufferedImage finalFractal = mandelbrotService.generateImage(
                    segmentedImage.getWidth(), segmentedImage.getHeight());

            // XOR операция
            BufferedImage encryptedImage = XOR.performXOR(segmentedImage, finalFractal);

            // Создаем EncryptionResult для cryptographicService
            var encryptionResult = new com.cipher.core.dto.encryption.EncryptionResult(
                    segmentedImage,
                    finalFractal,
                    new com.cipher.core.dto.encryption.EncryptionParams(
                            new com.cipher.core.dto.encryption.EncryptionArea(
                                    0, 0, segmentedImage.getWidth(), segmentedImage.getHeight(), true
                            ),
                            new com.cipher.core.dto.segmentation.SegmentationParams(
                                    segmentationResult.segmentSize(),
                                    segmentationResult.paddedWidth(),
                                    segmentationResult.paddedHeight(),
                                    segmentationResult.segmentMapping()
                            ),
                            mandelbrotService.getCurrentParams()
                    )
            );

            // Шифруем параметры
            var encryptedDataResult = cryptographicService.encryptData(encryptionResult);

            return new TestEncryptionResult(
                    encryptedImage,
                    finalFractal,
                    encryptedDataResult,
                    mandelbrotService.getCurrentParams()
            );

        } catch (Exception e) {
            logger.error("Ошибка шифрования: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Сохранение результатов итерации
     */
    private void saveIterationResults(TestEncryptionResult result, int iteration) {
        try {
            // Сохраняем зашифрованное изображение с номером итерации
            ImageIO.write(result.encryptedImage(), "PNG",
                    new File(outputDirectory + "encrypted_" + iteration + ".png"));

            // Сохраняем фрактал с номером итерации
            ImageIO.write(result.fractalImage(), "PNG",
                    new File(outputDirectory + "fractal_" + iteration + ".png"));

            // Сохраняем параметры Мандельброта в бинарный файл
            binaryFile.saveMandelbrotParamsToBinaryFile(
                    outputDirectory + "mandelbrot_params_" + iteration + ".bin",
                    result.mandelbrotParams()
            );

            // Сохраняем зашифрованные параметры в бинарный файл
            saveEncryptedParams(result.encryptedDataResult(), iteration);

            logger.debug("💾 Результаты итерации {} сохранены", iteration);

        } catch (IOException e) {
            logger.error("Ошибка сохранения результатов итерации {}: {}", iteration, e.getMessage());
        }
    }

    /**
     * Сохранение зашифрованных параметров в бинарный файл
     */
    private void saveEncryptedParams(com.cipher.core.dto.encryption.EncryptionDataResult encryptedParams, int iteration) {
        try {
            // Создаем структуру бинарного файла
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);

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

            // Сохраняем в файл с номером итерации
            saveBinaryFile("encryption_params_" + iteration + ".bin", binaryData);

        } catch (IOException e) {
            logger.error("Ошибка сохранения зашифрованных параметров: {}", e.getMessage());
        }
    }

    private void saveBinaryFile(String filename, byte[] data) throws IOException {
        Path path = Paths.get(outputDirectory, filename);
        Files.write(path, data, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Логирование прогресса
     */
    private void logProgress(int currentIteration, int successCount, int failCount) {
        double progress = (double) currentIteration / TOTAL_ITERATIONS * 100;
        logger.info("📊 Прогресс: {}/{} ({:.1f}%) - Успешно: {}, Ошибок: {}",
                currentIteration, TOTAL_ITERATIONS, progress, successCount, failCount);
    }

    /**
     * Логирование финальных результатов
     */
    private void logFinalResults(int successCount, int failCount, long totalTime) {
        logger.info("=".repeat(60));
        logger.info("🎉 ТЕСТИРОВАНИЕ ЗАВЕРШЕНО");
        logger.info("⏱️  Общее время: {} мс ({} сек)", totalTime, totalTime / 1000);
        logger.info("✅ Успешных итераций: {}", successCount);
        logger.info("❌ Неудачных итераций: {}", failCount);
        logger.info("📈 Успешность: {:.2f}%", (double) successCount / TOTAL_ITERATIONS * 100);
        logger.info("💾 Результаты сохранены в: {}", outputDirectory);
        logger.info("=".repeat(60));
    }

    /**
     * Показ финального диалога с результатами
     */
    private void showFinalDialog(int successCount, int failCount, long totalTime) {
        String message = String.format(
                "Тестирование завершено!\n\n" +
                        "Успешных итераций: %d\n" +
                        "Неудачных итераций: %d\n" +
                        "Общее время: %.1f секунд\n" +
                        "Успешность: %.1f%%\n\n" +
                        "Результаты сохранены в:\n%s",
                successCount, failCount, totalTime / 1000.0,
                (double) successCount / TOTAL_ITERATIONS * 100,
                outputDirectory
        );

        dialogDisplayer.showAlert("Тестирование завершено", message);
    }

    // Вспомогательный класс для хранения результатов теста
    private record TestEncryptionResult(
            BufferedImage encryptedImage,
            BufferedImage fractalImage,
            com.cipher.core.dto.encryption.EncryptionDataResult encryptedDataResult,
            com.cipher.core.dto.MandelbrotParams mandelbrotParams
    ) {}

    // Статический класс XOR операции
    private static class XOR {
        public static BufferedImage performXOR(BufferedImage img1, BufferedImage img2) {
            int width = img1.getWidth();
            int height = img1.getHeight();
            BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgb1 = img1.getRGB(x, y);
                    int rgb2 = img2.getRGB(x, y);
                    int xor = rgb1 ^ rgb2;
                    result.setRGB(x, y, xor | 0xFF000000);
                }
            }
            return result;
        }
    }
}