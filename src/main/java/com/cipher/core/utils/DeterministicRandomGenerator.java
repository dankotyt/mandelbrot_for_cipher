package com.cipher.core.utils;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Детерминированный генератор псевдослучайных чисел на основе SecureRandom.
 * При инициализации одним и тем же мастер-ключом всегда produces одинаковую последовательность значений.
 * Реализует интерфейс Random для совместимости с методами вроде Collections.shuffle.
 */
@Component
public class DeterministicRandomGenerator extends Random {

    private SecureRandom secureRandom;
    private byte[] currentSeed;

    /**
     * Создает детерминированный генератор на основе мастер-ключа.
     */
    public DeterministicRandomGenerator() {
        try {
            this.secureRandom = SecureRandom.getInstance("DRBG");
        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации детерминированного генератора", e);
        }
    }

    public synchronized void initialize(byte[] seed) {
        this.currentSeed = Arrays.copyOf(seed, seed.length);
        this.secureRandom.setSeed(seed);
    }

    public synchronized void reinitialize() {
        if (currentSeed != null) {
            secureRandom.setSeed(currentSeed);
        }
    }

    // ========== ОСНОВНЫЕ МЕТОДЫ ==========

    @Override
    public int nextInt() {
        return secureRandom.nextInt();
    }

    @Override
    public int nextInt(int bound) {
        return secureRandom.nextInt(bound);
    }

    @Override
    public double nextDouble() {
        return secureRandom.nextDouble();
    }

    @Override
    public long nextLong() {
        return secureRandom.nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return secureRandom.nextBoolean();
    }

    @Override
    public void nextBytes(byte[] bytes) {
        secureRandom.nextBytes(bytes);
    }

    @Override
    public float nextFloat() {
        return secureRandom.nextFloat();
    }

    // ========== СПЕЦИАЛИЗИРОВАННЫЕ МЕТОДЫ ==========

    /**
     * Генерирует размер сегмента в зависимости от размера изображения
     */
    public int generateSegmentSize(int imageWidth, int imageHeight) {
        reinitialize();
        int maxDimension = Math.max(imageWidth, imageHeight);

        if (maxDimension <= 768) return 4;
        else if (maxDimension <= 1920) return 16;
        else return 32;
    }

    /**
     * Перемешивает список детерминированным образом
     */
    public <T> void shuffleList(List<T> list) {
        reinitialize();
        //Collections.shuffle(list, this);

        // Алгоритм Фишера-Йетса с использованием secureRandom
        for (int i = list.size() - 1; i > 0; i--) {
            int j = secureRandom.nextInt(i + 1);
            T temp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, temp);
        }
    }

    /**
     * Шифрует данные с использованием AES на основе мастер-ключа
     */
    public byte[] encryptData(byte[] data) throws Exception {
        byte[] key = generateAESKey();
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher.doFinal(data);
    }

    /**
     * Дешифрует данные с использованием AES на основе мастер-ключа
     */
    public byte[] decryptData(byte[] encryptedData) throws Exception {
        byte[] key = generateAESKey();
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        return cipher.doFinal(encryptedData);
    }

    /**
     * Генерирует AES ключ на основе текущего состояния генератора
     */
    private byte[] generateAESKey() {
        byte[] key = new byte[32]; // 256-bit key
        nextBytes(key);
        return key;
    }
}
