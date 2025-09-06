package com.cipher.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/**
 * Конфигурационный класс для настройки криптографических компонентов.
 * Регистрирует бины для работы с алгоритмами шифрования и подписи.
 * Обеспечивает централизованное управление криптографическими ресурсами.
 *
 * @implNote Все создаваемые бины являются потокобезопасными за исключением Signature,
 *           который помечен как prototype для потокобезопасности.
 */
@Configuration
public class CryptoConfig {

    private static final Logger logger = LoggerFactory.getLogger(CryptoConfig.class);

    /**
     * Создает и возвращает экземпляр KeyFactory для алгоритма EdDSA.
     * KeyFactory используется для преобразования ключей между различными форматами
     * (например, из X509EncodedKeySpec в PublicKey).
     *
     * @return настроенный экземпляр KeyFactory для EdDSA
     * @throws IllegalStateException если алгоритм EdDSA недоступен в текущем окружении
     *
     * @see KeyFactory
     * @see X509EncodedKeySpec
     */
    @Bean
    public KeyFactory keyFactory() {
        try {
            return KeyFactory.getInstance("EdDSA");
        } catch (NoSuchAlgorithmException e) {
            logger.error("EdDSA KeyFactory not available", e);
            throw new IllegalStateException("EdDSA algorithm not available", e);
        }
    }

    /**
     * Создает прототип экземпляра Signature для алгоритма EdDSA.
     * Signature не является потокобезопасным, поэтому используется scope prototype
     * для создания нового экземпляра при каждом обращении.
     *
     * @return новый экземпляр Signature для алгоритма EdDSA
     * @throws IllegalStateException если алгоритм EdDSA недоступен в текущем окружении
     *
     * @see Signature
     * @implSpec Scope#prototype
     */
    @Bean
    @Scope("prototype")
    public Signature signature() {
        try {
            return Signature.getInstance("EdDSA");
        } catch (NoSuchAlgorithmException e) {
            logger.error("EdDSA Signature not available", e);
            throw new IllegalStateException("EdDSA signature not available", e);
        }
    }
}
