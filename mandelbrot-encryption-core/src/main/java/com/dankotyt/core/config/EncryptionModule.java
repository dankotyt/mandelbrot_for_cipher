package com.dankotyt.core.config;

import com.dankotyt.core.service.encryption.ImageDecryptor;
import com.dankotyt.core.service.encryption.ImageEncryptor;
import com.dankotyt.core.service.encryption.MandelbrotService;
import com.dankotyt.core.service.encryption.impl.*;
import com.dankotyt.core.service.network.CryptoKeyManager;
import com.dankotyt.core.service.network.impl.ECDHCryptoKeyManagerImpl;
import com.dankotyt.core.utils.ImageUtils;

/**
 * Фабрика для создания экземпляров модуля шифрования без использования Spring.
 * <p>
 * Предоставляет простой способ получить готовые к использованию реализации
 * {@link ImageEncryptor}, {@link ImageDecryptor} и {@link CryptoKeyManager}
 * со всеми необходимыми зависимостями (MandelbrotService, SegmentShuffler, ECDHService и т.д.).
 * </p>
 * <p>
 * <b>Пример использования:</b>
 * <pre>{@code
 * // Создание менеджера ключей и обмен с пиром
 * CryptoKeyManager keyManager = EncryptionModule.createKeyManager();
 * // ... настройка пира, обмен публичными ключами ...
 * byte[] sharedSecret = keyManager.getMasterSeedFromDH(peerAddress);
 *
 * // Шифрование изображения
 * ImageEncryptor encryptor = EncryptionModule.createEncryptor();
 * encryptor.prepareSession(sharedSecret);
 * EncryptedData encrypted = encryptor.encryptWhole(image);
 *
 * // Дешифрование (требуется тот же keyManager)
 * ImageDecryptor decryptor = EncryptionModule.createDecryptor(keyManager);
 * BufferedImage decrypted = decryptor.decryptImage(encryptedFile);
 * }</pre>
 * </p>
 * <p>
 * Этот класс не зависит от Spring и может использоваться в любом Java-приложении.
 * </p>
 *
 * @author dankotyt
 * @since 1.1.0
 */
public class EncryptionModule {

    private EncryptionModule() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Создаёт экземпляр {@link CryptoKeyManager} с настройками по умолчанию.
     * <p>
     * Использует {@link ECDHServiceImpl} для генерации ключей и управления обменом.
     * </p>
     *
     * @return готовый к работе менеджер ключей
     */
    public static CryptoKeyManager createKeyManager() {
        return new ECDHCryptoKeyManagerImpl(new ECDHServiceImpl());
    }

    /**
     * Создаёт экземпляр {@link ImageEncryptor} для шифрования изображений.
     * <p>
     * Внутри создаются все необходимые компоненты: {@link MandelbrotService},
     * {@link ImageSegmentShufflerImpl} и {@link ImageUtils}.
     * </p>
     *
     * @return готовый шифратор
     */
    public static ImageEncryptor createEncryptor() {
        return new ImageEncryptorImpl(
                new MandelbrotService(),
                new ImageSegmentShufflerImpl(new SegmentSizeStrategyImpl()),
                new ImageUtils()
        );
    }

    /**
     * Создаёт экземпляр {@link ImageDecryptor} для дешифрования изображений.
     * <p>
     * Требует передачи {@link CryptoKeyManager}, который будет использоваться
     * для получения общего секрета при дешифровании.
     * </p>
     *
     * @param keyManager менеджер ключей (уже настроенный и подключённый к пиру)
     * @return готовый дешифратор
     */
    public static ImageDecryptor createDecryptor(CryptoKeyManager keyManager) {
        return new ImageDecryptorImpl(
                new MandelbrotService(),
                new ImageSegmentShufflerImpl(new SegmentSizeStrategyImpl()),
                new ImageUtils(),
                keyManager
        );
    }
}
