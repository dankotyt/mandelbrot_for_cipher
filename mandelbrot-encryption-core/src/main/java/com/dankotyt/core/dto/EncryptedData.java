package com.dankotyt.core.dto;

/**
 * DTO, содержащее все метаданные и зашифрованное изображение.
 * Используется как результат шифрования и для последующего дешифрования.
 *
 * @param sessionSalt   случайная соль сессии (16 байт)
 * @param attemptCount   номер попытки (для детерминированного воспроизведения)
 * @param startX         X-координата левого верхнего угла зашифрованной области
 * @param startY         Y-координата левого верхнего угла зашифрованной области
 * @param areaWidth      ширина зашифрованной области
 * @param areaHeight     высота зашифрованной области
 * @param originalWidth  полная ширина исходного изображения
 * @param originalHeight полная высота исходного изображения
 * @param imageBytes     зашифрованные пиксельные данные в формате RGB (3 байта на пиксель)
 *
 * @author dankotyt
 * @since 1.1.0
 */
public record EncryptedData(byte[] sessionSalt, int attemptCount, int startX, int startY,
                            int areaWidth, int areaHeight, int originalWidth, int originalHeight,
                            byte[] imageBytes) {}