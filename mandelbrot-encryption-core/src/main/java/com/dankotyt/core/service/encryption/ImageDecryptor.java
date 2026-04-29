package com.dankotyt.core.service.encryption;

import java.awt.image.BufferedImage;
import java.io.File;
import java.net.InetAddress;

/**
 * Дешифратор изображений, зашифрованных с помощью {@link ImageEncryptor}.
 * Для восстановления использует общий секрет, согласованный с конкретным пиром.
 *
 * @author dankotyt
 * @since 1.1.0
 */
public interface ImageDecryptor {
    /**
     * Расшифровывает изображение из файла, используя ключ, связанный с указанным пиром.
     *
     * @param encryptedFile файл с зашифрованными данными (формат, описанный в {@link ImageEncryptor}).
     * @param peerAddress   IP-адрес пира, для которого брать общий секрет.
     * @return восстановленное изображение.
     * @throws Exception если возникают ошибки ввода-вывода, повреждены данные или отсутствует ключ для пира.
     */
    BufferedImage decryptImage(File encryptedFile, InetAddress peerAddress) throws Exception;
}