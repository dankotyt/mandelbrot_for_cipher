package com.cipher.client.service.impl;

import com.cipher.client.exceptions.SeedGenerationException;
import com.cipher.client.feign.AccountApiClient;
import com.cipher.client.service.SeedService;
import com.cipher.client.utils.KeysUtils;
import com.cipher.client.utils.NetworkUtils;
import com.cipher.common.dto.AuthRequest;
import com.cipher.common.exception.AuthException;
import com.cipher.common.exception.CryptoException;
import com.cipher.common.exception.NetworkException;
import lombok.RequiredArgsConstructor;
import org.bitcoinj.crypto.MnemonicCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.security.*;
import java.util.Arrays;
import java.util.List;

/**
 * Реализация сервиса {@link SeedService} для генерации seed-фраз и создания аккаунтов.
 * Обеспечивает создание новой seed-фразы и регистрацию аккаунта на сервере.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SeedServiceImpl implements SeedService {
    private final AccountApiClient accountApiClient;

    /**
     * Генерирует новую seed-фразу и регистрирует аккаунт на сервере.
     *
     * @return сгенерированная seed-фраза в виде строки из 12 слов
     * @throws AuthException если аккаунт с таким ключом уже существует
     * @throws NetworkException если возникли проблемы с сетью
     * @throws CryptoException если произошла ошибка генерации ключей
     * @throws SeedGenerationException если не удалось сгенерировать seed-фразу
     */
    public String generateAccount() {
        NetworkUtils.checkNetworkConnection();
        List<String> words = null;
        try {
            words = generatedWordsForSeed();
            KeysUtils.CryptoKeys keys = KeysUtils.createKeysFromWords(words);

            String publicKeyBase64 = KeysUtils.getPublicKeyBase64(keys.publicKey());
            AuthRequest request = new AuthRequest(keys.userId(), publicKeyBase64);

            accountApiClient.generateAccount(request);

            return String.join(" ", words);

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new AuthException("Аккаунт с таким ключом уже существует");
            } else {
                throw new NetworkException("Ошибка создания аккаунта: " + e.getStatusCode(), e);
            }
        } catch (ResourceAccessException e) {
            throw new NetworkException("Нет подключения к серверу. Проверьте интернет-соединение", e);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Ошибка генерации криптографических ключей", e);
        } catch (Exception e) {
            throw new SeedGenerationException("Ошибка генерации seed-фразы", e);
        } finally {
            if (words != null) {
                words.clear();
            }
        }
    }

    /**
     * Генерирует мнемоническую фразу из 12 слов на основе энтропии.
     *
     * @return список из 12 слов мнемонической фразы
     * @throws SeedGenerationException если генерация не удалась
     */
    private List<String> generatedWordsForSeed() {
        byte[] entropy = null;
        try {
            SecureRandom random = new SecureRandom();
            entropy = new byte[16];
            random.nextBytes(entropy);

            MnemonicCode mnemonicCode = MnemonicCode.INSTANCE;
            return mnemonicCode.toMnemonic(entropy);
        } catch (Exception e) {
            throw new SeedGenerationException("Ошибка генерации мнемонической фразы", e);
        } finally {
            if (entropy != null) {
                Arrays.fill(entropy, (byte) 0);
            }
        }
    }
}
