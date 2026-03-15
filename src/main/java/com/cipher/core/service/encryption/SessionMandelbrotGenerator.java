package com.cipher.core.service.encryption;

import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.threading.MandelbrotThread;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
@Deprecated
public class SessionMandelbrotGenerator {

    public static final int SESSION_FRACTAL_WIDTH = 1024;
    public static final int SESSION_FRACTAL_HEIGHT = 768;

    private Random random;
    private MandelbrotParams sessionParams;
    /**
     * -- GETTER --
     *  Проверить, инициализирован ли генератор
     */
    @Getter
    private boolean initialized = false;

    // Кэшированные данные сессионного фрактала
    private byte[] cachedFractalBytes;
    private BufferedImage cachedFractalImage;

    /**
     * Инициализация seed - общим секретом из ECDH
     */
    public void initializeWithSeed(byte[] seed) {
        long longSeed = new BigInteger(1, seed).longValue();
        this.random = new Random(longSeed);
        generateSessionParams();
        generateFractal();
        this.initialized = true;
    }

    /**
     * Генерирует параметры сессионного фрактала (один раз)
     */
    private void generateSessionParams() {
        double zoom = 100000 + (random.nextInt(44) * 1000);
        double offsetX = -0.9998 + (random.nextDouble() * (0.9998 - -0.9998));
        double offsetY = -0.9998 + (random.nextDouble() * (0.9998 - -0.9998));
        int maxIter = 300 + (random.nextInt(91) * 10);
        this.sessionParams = new MandelbrotParams(zoom, offsetX, offsetY, maxIter);
    }

    /**
     * Генерирует сессионный фрактал (один раз при инициализации)
     */
    private void generateFractal() {
        BufferedImage image = new BufferedImage(
                SESSION_FRACTAL_WIDTH,
                SESSION_FRACTAL_HEIGHT,
                BufferedImage.TYPE_INT_RGB
        );

        int processors = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(processors);
        List<Future<?>> futures = new ArrayList<>();

        try {
            int chunkWidth = SESSION_FRACTAL_WIDTH / processors;

            for (int i = 0; i < processors; i++) {
                int startX = i * chunkWidth;
                int w = (i == processors - 1) ? SESSION_FRACTAL_WIDTH - startX : chunkWidth;

                MandelbrotThread thread = new MandelbrotThread(
                        startX, 0, w, SESSION_FRACTAL_HEIGHT,
                        sessionParams.zoom(),
                        sessionParams.maxIter(),
                        sessionParams.offsetX(),
                        sessionParams.offsetY(),
                        image
                );

                futures.add(executor.submit(thread));
            }

            for (Future<?> future : futures) {
                future.get();
            }

        } catch (Exception e) {
            throw new RuntimeException("Ошибка при генерации сессионного фрактала", e);
        } finally {
            executor.shutdown();
        }

        // Кэшируем изображение и его байты
        this.cachedFractalImage = image;
        this.cachedFractalBytes = fractalToBytes(image);
    }

    /**
     * Получить байты сессионного фрактала
     * @return массив байт сессионного фрактала (1024x768)
     */
    public byte[] getSessionFractalBytes() {
        if (!initialized) {
            throw new IllegalStateException("SessionMandelbrotGenerator не инициализирован");
        }
        return cachedFractalBytes;
    }

    /**
     * Получить изображение сессионного фрактала
     * @return BufferedImage сессионного фрактала (1024x768)
     */
    public BufferedImage getSessionFractalImage() {
        if (!initialized) {
            throw new IllegalStateException("SessionMandelbrotGenerator не инициализирован");
        }
        return cachedFractalImage;
    }

    /**
     * Получить параметры сессионного фрактала
     */
    public MandelbrotParams getSessionParams() {
        if (!initialized) {
            throw new IllegalStateException("Session generator not initialized");
        }
        return sessionParams;
    }

    /**
     * Получить ширину сессионного фрактала
     */
    public int getWidth() {
        return SESSION_FRACTAL_WIDTH;
    }

    /**
     * Получить высоту сессионного фрактала
     */
    public int getHeight() {
        return SESSION_FRACTAL_HEIGHT;
    }

    /**
     * Очистка кэша (при дисконнекте)
     */
    @PreDestroy
    public void clearCache() {
        cachedFractalBytes = null;
        cachedFractalImage = null;
        initialized = false;
    }

    /**
     * Конвертирует изображение в массив байт
     */
    private byte[] fractalToBytes(BufferedImage fractal) {
        byte[] bytes = new byte[SESSION_FRACTAL_WIDTH * SESSION_FRACTAL_HEIGHT * 3];

        int index = 0;
        for (int y = 0; y < SESSION_FRACTAL_HEIGHT; y++) {
            for (int x = 0; x < SESSION_FRACTAL_WIDTH; x++) {
                int rgb = fractal.getRGB(x, y);
                bytes[index++] = (byte) ((rgb >> 16) & 0xFF);
                bytes[index++] = (byte) ((rgb >> 8) & 0xFF);
                bytes[index++] = (byte) (rgb & 0xFF);
            }
        }
        return bytes;
    }
}