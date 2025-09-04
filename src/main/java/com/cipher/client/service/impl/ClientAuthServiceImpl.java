package com.cipher.client.service.impl;

import com.cipher.client.feign.AuthApiClient;
import com.cipher.client.service.ClientAuthService;
import com.cipher.client.service.SeedService;
import com.cipher.client.utils.KeysUtils;
import com.cipher.client.utils.NetworkUtils;
import com.cipher.common.dto.AuthResponse;
import com.cipher.common.dto.LoginRequest;
import com.cipher.common.dto.NonceRequest;
import com.cipher.common.dto.NonceResponse;
import com.cipher.common.exception.AuthException;
import com.cipher.common.exception.CryptoException;
import com.cipher.common.exception.NetworkException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.List;

/**
 * Реализация сервиса {@link ClientAuthService} для аутентификации клиента.
 * Обеспечивает процесс входа пользователя с использованием seed-фразы
 * и криптографической подписи.
 */
@Service
@RequiredArgsConstructor
public class ClientAuthServiceImpl implements ClientAuthService {
    private static final Logger logger = LoggerFactory.getLogger(ClientAuthServiceImpl.class);

    private final AuthApiClient authApiClient;

    /**
     * Выполняет процесс аутентификации пользователя на основе seed-фразы.
     *
     * @param words список из 12 слов seed-фразы
     * @return access token для аутентифицированного пользователя
     * @throws AuthException если аутентификация не удалась или seed-фраза неверна
     * @throws NetworkException если возникли проблемы с сетью
     * @throws CryptoException если произошла ошибка криптографических операций
     * @throws IllegalArgumentException если передан неверный формат seed-фразы
     */
    public String login(List<String> words) {
        try {
            NetworkUtils.checkNetworkConnection();
            validateWords(words);

            KeysUtils.CryptoKeys keys = KeysUtils.createKeysFromWords(words);

            NonceResponse nonceResponse = authApiClient.requestNonce(new NonceRequest(keys.userId()));

            byte[] signature = signData(nonceResponse.nonce().getBytes(StandardCharsets.UTF_8), keys.privateKey());
            String signatureBase64 = Base64.getEncoder().encodeToString(signature);

            AuthResponse authResponse = authApiClient.login(new LoginRequest(keys.userId(), signatureBase64));

            return authResponse.accessToken();

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new AuthException("Аккаунт не найден. Проверьте seed-фразу");
            } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new AuthException("Неверная подпись или истек срок действия nonce");
            } else {
                throw new NetworkException("Ошибка сервера: " + e.getStatusCode(), e);
            }
        } catch (HttpServerErrorException e) {
            throw new NetworkException("Внутренняя ошибка сервера", e);
        } catch (ResourceAccessException e) {
            throw new NetworkException("Нет подключения к серверу. Проверьте интернет-соединение", e);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Ошибка криптографических операций", e);
        } catch (Exception e) {
            throw new AuthException("Ошибка авторизации. Проверьте введенную seed-фразу!", e);
        }
    }

    /**
     * Валидирует формат seed-фразы.
     *
     * @param words список слов для валидации
     * @throws AuthException если фраза не содержит 12 непустых слов
     */
    private void validateWords(List<String> words) {
        if (words == null || words.size() != 12) {
            throw new AuthException("Требуется ровно 12 слов seed-фразы");
        }

        for (String word : words) {
            if (word == null || word.trim().isEmpty()) {
                throw new AuthException("Все слова должны быть заполнены");
            }
        }
    }

    /**
     * Подписывает данные с использованием приватного ключа.
     *
     * @param data данные для подписи в виде byte array
     * @param privateKey приватный ключ для подписи
     * @return подпись в виде byte array
     * @throws CryptoException если подпись не удалась
     */
    private byte[] signData(byte[] data, PrivateKey privateKey) throws GeneralSecurityException {
        try {
            Signature signer = Signature.getInstance("EdDSA");
            signer.initSign(privateKey);
            signer.update(data);
            return signer.sign();
        } catch (GeneralSecurityException e) {
            logger.error("Ошибка подписи данных", e);
            throw new CryptoException("Не удалось подписать данные", e);
        }
    }
}
