package com.cipher.core.service.encryption;

import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.encryption.HKDF;
import com.cipher.core.encryption.ImageSegmentShuffler;
import com.cipher.core.service.network.CryptoKeyManager;
import com.cipher.core.threading.MandelbrotThread;
import com.cipher.core.utils.ConsoleManager;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

import com.cipher.core.utils.ImageUtils;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author @dankotyt Danil Kotlyarov
 * Класс Mandelbrot представляет собой графический компонент Swing, который генерирует изображение множества Мандельброта.
 * Он позволяет пользователю сохранять сгенерированные изображения на рабочий стол и использует многопоточность для ускорения
 * генерации изображения и проверки его разнообразия.
 */
@Component
@RequiredArgsConstructor
public class MandelbrotService extends JPanel {
    private static final Logger logger = LoggerFactory.getLogger(MandelbrotService.class);

    private final ImageSegmentShuffler imageSegmentShuffler;
    private final ImageUtils imageUtils;
    private final CryptoKeyManager cryptoKeyManager;

    private SecureRandom paramsPrng;
    @Getter
    private int attemptCount;
    @Getter
    private byte[] sessionSalt;
    private int targetWidth;
    private int targetHeight;
    private int startMandelbrotWidth;
    private int startMandelbrotHeight;
    private double ZOOM;
    private int MAX_ITER;
    private double offsetX;
    private double offsetY;
    private BufferedImage image;
    @Getter
    private MandelbrotParams currentParams;

    private String getProjectRootPath() {
        return new File("").getAbsolutePath() + File.separator;
    }

    private String getTempPath() {
        return getProjectRootPath() + "temp" + File.separator;
    }

    /**
     * Переопределяет метод paintComponent для отрисовки сгенерированного изображения множества Мандельброта.
     *
     * @param g Графический контекст для рисования.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image != null) {
            g.drawImage(image, 0, 0, null);
        }
    }

    public void setTargetSize(int width, int height) {
        this.targetWidth = width;
        this.targetHeight = height;
    }

    public void prepareSession(byte[] sharedSecret) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        this.sessionSalt = salt;

        byte[] prk = HKDF.extract(salt, sharedSecret);
        byte[] keyFractalParams = HKDF.expand(prk, "fractal-params".getBytes(StandardCharsets.UTF_8), 32);
        byte[] keySegmentation = HKDF.expand(prk, "segmentation".getBytes(StandardCharsets.UTF_8), 32);

        this.paramsPrng = SecureRandom.getInstance("SHA1PRNG");
        this.paramsPrng.setSeed(keyFractalParams);

        // Инициализация ImageSegmentShuffler
        imageSegmentShuffler.initialize(keySegmentation);

        this.attemptCount = 0;
    }

    private MandelbrotParams generateParams() {
        double zoom = 1000 + (paramsPrng.nextInt(1000) * 100);
        double offsetX = -0.9998 + paramsPrng.nextDouble() * (0.45 - (-0.9998));
        double offsetY;
        if (paramsPrng.nextBoolean()) {
            offsetY = -0.7 + paramsPrng.nextDouble() * 0.6; // интервал [-0.7, -0.1]
        } else {
            offsetY = 0.1 + paramsPrng.nextDouble() * 0.6;  // интервал [0.1, 0.7]
        }
        int maxIter = 300;
        return new MandelbrotParams(zoom, offsetX, offsetY, maxIter);
    }

    public MandelbrotParams generateParams(SecureRandom prng) {
        double zoom = 100000 + (prng.nextInt(44) * 1000);
        double offsetX = -0.9998 + (prng.nextDouble() * (0.9998 - -0.9998));
        double offsetY = -0.9998 + (prng.nextDouble() * (0.9998 - -0.9998));
        int maxIter = 300 + (prng.nextInt(91) * 10);
        return new MandelbrotParams(zoom, offsetX, offsetY, maxIter);
    }

    public BufferedImage generateImage() {
        if (paramsPrng == null) {
            throw new IllegalStateException("Session not prepared. Call prepareSession first.");
        }
        MandelbrotParams params = generateParams();
        this.currentParams = params;
        this.attemptCount++;
        return generateImage(targetWidth, targetHeight,
                params.zoom(), params.offsetX(), params.offsetY(), params.maxIter());
    }

    /**
     * Генерирует изображение множества Мандельброта и проверяет его разнообразие.
     * <p>
     * Алгоритм:
     * <ol>
     *   <li>Генерирует изображение с использованием многопоточной обработки</li>
     *   <li>Проверяет качество изображения (достаточное разнообразие цветов)</li>
     *   <li>Повторяет процесс, если изображение не удовлетворяет критериям</li>
     *   <li>Сохраняет параметры генерации во временный файл</li>
     * </ol>
     *
     * @return сгенерированное изображение или null, если поток был прерван
     * @see #checkImageDiversity(BufferedImage)
     */
//    public BufferedImage generateImage() {
//        InetAddress peerAddress = cryptoKeyManager.getConnectedPeer();
//        if (peerAddress == null) {
//            Map<InetAddress, String> activeConnections = cryptoKeyManager.getActiveConnections();
//            if (!activeConnections.isEmpty()) {
//                peerAddress = activeConnections.keySet().iterator().next();
//                cryptoKeyManager.setConnectedPeer(peerAddress);
//            } else {
//                throw new IllegalStateException("No connected peer found. Please establish connection first.");
//            }
//        }
//
//        if (!cryptoKeyManager.hasKeysForPeer(peerAddress.getHostAddress())) {
//            throw new IllegalStateException("No encryption keys available for peer: " +
//                    peerAddress.getHostAddress() + ". Please perform key exchange first.");
//        }
//
//        // Получаем мастер-сид из DH обмена
//        byte[] masterSeed = cryptoKeyManager.getMasterSeedFromDH(peerAddress);
//
//        // Инициализируем сервисы мастер-сидом
//        imageSegmentShuffler.initializeWithSeed(masterSeed);
//
//        boolean validImage = false;
//        int attempt = 0;
//        BufferedImage resultImage = null;
//
//        while (!validImage && !Thread.currentThread().isInterrupted()) {
//            attempt++;
//            randomOneTimePosition();
//
//            try (ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
//                resultImage = new BufferedImage(startMandelbrotWidth, startMandelbrotHeight, BufferedImage.TYPE_INT_RGB);
//                int processors = Runtime.getRuntime().availableProcessors();
//                int chunkWidth = startMandelbrotWidth / processors;
//
//                List<Future<?>> futures = new ArrayList<>();
//                for (int i = 0; i < processors; i++) {
//                    int startX = i * chunkWidth;
//                    int width = (i == processors - 1) ? startMandelbrotWidth - startX : chunkWidth;
//
//                    futures.add(executor.submit(new MandelbrotThread(
//                            startX, 0, width, startMandelbrotHeight,
//                            ZOOM, MAX_ITER, offsetX, offsetY, resultImage
//                    )));
//                }
//
//                // Ожидаем завершения всех задач
//                for (Future<?> future : futures) {
//                    future.get();
//                }
//
//                currentParams = new MandelbrotParams(
//                        ZOOM,
//                        offsetX,
//                        offsetY,
//                        MAX_ITER
//                );
//
//                validImage = checkImageDiversity(resultImage);
//
//                if (!validImage) {
//                    ConsoleManager.log("Попытка №" + attempt + ". Подождите, пожалуйста...");
//                } else {
//                    ConsoleManager.log("Изображение успешно сгенерировано после " + attempt + " попыток.");
//                    /**
//                     * todo проверить, правильно ли используются потом сгенерированные параметры,
//                     * которые используются в ImageEncrypt.wholeImage()
//                     * */
//                    imageUtils.setMandelbrotImage(resultImage, currentParams);
//                }
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//                logger.error("Генерация прервана", e);
//                return null;
//            } catch (ExecutionException e) {
//                logger.error("Ошибка в потоке вычислений (попытка " + attempt + ")", e);
//            }
//        }
//
//        repaint();
//        return resultImage;
//    }

    public BufferedImage generateImage(int originalWidth, int originalHeight) {
        BufferedImage resultImage = new BufferedImage(originalWidth, originalHeight, BufferedImage.TYPE_INT_RGB);

        try (ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            int processors = Runtime.getRuntime().availableProcessors();
            int chunkWidth = originalWidth / processors;

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < processors; i++) {
                int startX = i * chunkWidth;
                int width = (i == processors - 1) ? originalWidth - startX : chunkWidth;

                futures.add(executor.submit(new MandelbrotThread(
                        startX, 0, width, originalHeight,
                        currentParams.zoom(), currentParams.maxIter(),
                        currentParams.offsetX(), currentParams.offsetY(), resultImage
                )));
            }

            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Генерация прервана", e);
            return null;
        } catch (ExecutionException e) {
            logger.error("Ошибка в потоке вычислений", e);
            throw new RuntimeException("Ошибка генерации фрактала", e);
        }

        repaint();
        return resultImage;
    }

    /**
     * Генерирует изображение множества Мандельброта с заданными параметрами.
     * <p>
     * Использует многопоточную обработку для ускорения генерации. Разделяет изображение
     * на вертикальные полосы по количеству доступных процессоров.
     *
     * @param startMandelbrotWidth ширина генерируемого изображения
     * @param startMandelbrotHeight высота генерируемого изображения
     * @param ZOOM коэффициент масштабирования
     * @param offsetX смещение по оси X
     * @param offsetY смещение по оси Y
     * @param MAX_ITER максимальное количество итераций для алгоритма
     * @return сгенерированное изображение
     */
    public BufferedImage generateImage(int startMandelbrotWidth, int startMandelbrotHeight,
                                       double ZOOM, double offsetX, double offsetY, int MAX_ITER) {
        this.startMandelbrotWidth = startMandelbrotWidth;
        this.startMandelbrotHeight = startMandelbrotHeight;
        this.ZOOM = ZOOM;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.MAX_ITER = MAX_ITER;

        image = new BufferedImage(startMandelbrotWidth, startMandelbrotHeight, BufferedImage.TYPE_INT_RGB);

        try (ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            int processors = Runtime.getRuntime().availableProcessors();
            int chunkWidth = startMandelbrotWidth / processors;

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < processors; i++) {
                int startX = i * chunkWidth;
                int width = (i == processors - 1) ? startMandelbrotWidth - startX : chunkWidth;

                futures.add(executor.submit(new MandelbrotThread(
                        startX, 0, width, startMandelbrotHeight,
                        ZOOM, MAX_ITER, offsetX, offsetY, image
                )));
            }

            // Ожидаем завершения всех задач
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Генерация прервана", e);
            return null;
        } catch (ExecutionException e) {
            logger.error("Ошибка в потоке вычислений", e);
            throw new RuntimeException("Ошибка генерации фрактала", e);
        }

        repaint();
        return image;
    }

    /**
     * Проверяет, удовлетворяет ли изображение критериям разнообразия.
     * <p>
     * Критерии могут включать:
     * <ul>
     *   <li>Достаточное количество цветов</li>
     *   <li>Баланс между тёмными и светлыми областями</li>
     *   <li>Отсутствие слишком больших монотонных областей</li>
     * </ul>
     *
     * @param image изображение для проверки
     * @return true если изображение удовлетворяет критериям, иначе false
     */
    public boolean checkImageDiversity(BufferedImage image) {
        if (isImageBlackPercentageAboveThreshold(image, 0.05)) { //todo попробовать меньше значение
            return false;
        }

        // Оптимизированная проверка уникальности цветов
        Set<Integer> uniqueColors = new HashSet<>();
        int[] pixelBuffer = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        for (int j : pixelBuffer) {
            int color = j & 0x00FFFFFF;
            uniqueColors.add(color);

            // Быстрый выход если уже много уникальных цветов
            if (uniqueColors.size() > 1000) {
                break;
            }
        }

        // Проверка доминирующего цвета
        return uniqueColors.size() >= 250;
    }

    /**
     * Проверяет, превышает ли процент чёрных пикселей в изображении заданный порог.
     *
     * @param image изображение для анализа
     * @param threshold пороговое значение (от 0.0 до 1.0)
     * @return true, если процент чёрных пикселей превышает порог, иначе false
     */
    private static boolean isImageBlackPercentageAboveThreshold(BufferedImage image, double threshold) {
        int width = image.getWidth();
        int height = image.getHeight();
        int totalPixels = width * height;
        int blackPixelCount = 0;
        int maxBlackPixels = (int) (totalPixels * threshold) + 1;

        int[] pixelBuffer = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

        for (int pixel : pixelBuffer) {
            if (isBlackPixel(pixel)) {
                blackPixelCount++;
                if (blackPixelCount >= maxBlackPixels) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Определяет, является ли пиксель чёрным (RGB = 0,0,0).
     *
     * @param pixel значение пикселя в формате RGB
     * @return true если пиксель полностью чёрный, иначе false
     */
    private static boolean isBlackPixel(int pixel) {
        int red = (pixel >> 16) & 0xFF;
        int green = (pixel >> 8) & 0xFF;
        int blue = pixel & 0xFF;

        return red == 0 && green == 0 && blue == 0;
    }

    /**
     * Сохраняет изображение в папку resources в корне проекта.
     *
     * @param image Изображение для сохранения.
     */
    public void saveImageToTemp(BufferedImage image) {
        String savePath = getTempPath();
        File file = new File(savePath + "mandelbrot.png");

        try {
            ImageIO.write(image, "png", file);
        } catch (IOException e) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Ошибка сохранения");
                alert.setHeaderText(null);
                alert.setContentText("Ошибка при сохранении изображения: " + e.getMessage());
                alert.showAndWait();
            });
        }
    }

    /**
     * Конвертирует параметры в байты
     */
    public byte[] paramsToBytes(MandelbrotParams params) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeDouble(params.zoom());
        dos.writeDouble(params.offsetX());
        dos.writeDouble(params.offsetY());
        dos.writeInt(params.maxIter());
        return baos.toByteArray();
    }

    /**
     * Восстанавливает параметры из байт
     */
    public MandelbrotParams bytesToParams(byte[] bytes) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        DataInputStream dis = new DataInputStream(bais);
        return new MandelbrotParams(
                dis.readDouble(),
                dis.readDouble(),
                dis.readDouble(),
                dis.readInt()
        );
    }

    /**
     * Конвертирует изображение в массив байт
     */
    public byte[] fractalToBytes(BufferedImage fractal) {
        int width = fractal.getWidth();
        int height = fractal.getHeight();
        byte[] bytes = new byte[width * height * 3];

        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = fractal.getRGB(x, y);
                bytes[index++] = (byte) ((rgb >> 16) & 0xFF);
                bytes[index++] = (byte) ((rgb >> 8) & 0xFF);
                bytes[index++] = (byte) (rgb & 0xFF);
            }
        }
        return bytes;
    }

    /**
     * Восстанавливает изображение из байт
     */
    public BufferedImage bytesToFractal(byte[] bytes, int width, int height) {
        if (bytes.length != width * height * 3) {
            throw new IllegalArgumentException("Неверный размер массива байт");
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int index = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = bytes[index++] & 0xFF;
                int g = bytes[index++] & 0xFF;
                int b = bytes[index++] & 0xFF;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }
}