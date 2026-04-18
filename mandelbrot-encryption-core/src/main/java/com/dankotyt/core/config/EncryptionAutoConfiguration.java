package com.dankotyt.core.config;

import com.dankotyt.core.service.encryption.*;
import com.dankotyt.core.service.encryption.impl.ECDHServiceImpl;
import com.dankotyt.core.service.encryption.impl.ImageDecryptorImpl;
import com.dankotyt.core.service.encryption.impl.ImageEncryptorImpl;
import com.dankotyt.core.service.encryption.impl.ImageSegmentShufflerImpl;
import com.dankotyt.core.service.network.CryptoKeyManager;
import com.dankotyt.core.service.network.impl.ECDHCryptoKeyManagerImpl;
import com.dankotyt.core.utils.ImageUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Автоконфигурация Spring Boot для модуля шифрования.
 * <p>
 * Регистрирует все необходимые бины для работы с шифрованием изображений
 * и управления ECDH-ключами в сетевом окружении.
 * </p>
 * <p>
 * <b>Включаемые бины:</b>
 * <ul>
 *   <li>{@link ImageUtils} – утилиты для конвертации изображений</li>
 *   <li>{@link MandelbrotService} – генерация фракталов Мандельброта</li>
 *   <li>{@link SegmentShuffler} – перемешивание сегментов изображения</li>
 *   <li>{@link ECDHService} – криптография на эллиптических кривых</li>
 *   <li>{@link CryptoKeyManager} – управление ключами и общим секретом</li>
 *   <li>{@link ImageEncryptor} – шифрование изображений</li>
 *   <li>{@link ImageDecryptor} – дешифрование изображений</li>
 * </ul>
 * </p>
 * <p>
 * <b>Использование:</b>
 * <pre>{@code
 * @Service
 * public class MyService {
 *     @Autowired
 *     private ImageEncryptor encryptor;
 *     @Autowired
 *     private ImageDecryptor decryptor;
 *     @Autowired
 *     private CryptoKeyManager keyManager;
 *
 *     public void process(BufferedImage image, InetAddress peer) {
 *         byte[] secret = keyManager.getMasterSeedFromDH(peer);
 *         encryptor.prepareSession(secret);
 *         EncryptedData data = encryptor.encryptWhole(image);
 *         // ...
 *     }
 * }
 * }</pre>
 * </p>
 * <p>
 * <b>Примечание:</b> Данная конфигурация не зависит от JavaFX и UI-компонентов.
 * Для работы с файлами клиент должен самостоятельно реализовать сохранение зашифрованных данных.
 * </p>
 *
 * @author dankotyt
 * @since 1.1.1
 * @see EncryptionModule (альтернатива без Spring)
 */
@Configuration
public class EncryptionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ImageUtils imageUtils() {
        return new ImageUtils();
    }

    @Bean
    @ConditionalOnMissingBean
    public MandelbrotService mandelbrotService() {
        return new MandelbrotService();
    }

    @Bean
    @ConditionalOnMissingBean
    public SegmentShuffler segmentShuffler() {
        return new ImageSegmentShufflerImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public ECDHService ecdhService() {
        return new ECDHServiceImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public CryptoKeyManager cryptoKeyManager(ECDHService ecdhService) {
        return new ECDHCryptoKeyManagerImpl(ecdhService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ImageEncryptor imageEncryptor(MandelbrotService mandelbrotService,
                                         SegmentShuffler segmentShuffler,
                                         ImageUtils imageUtils) {
        return new ImageEncryptorImpl(mandelbrotService, segmentShuffler, imageUtils);
    }

    @Bean
    @ConditionalOnMissingBean
    public ImageDecryptor imageDecryptor(MandelbrotService mandelbrotService,
                                         SegmentShuffler segmentShuffler,
                                         ImageUtils imageUtils,
                                         CryptoKeyManager cryptoKeyManager) {
        return new ImageDecryptorImpl(mandelbrotService, segmentShuffler, imageUtils, cryptoKeyManager);
    }
}