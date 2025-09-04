package com.cipher.client.service;

import com.cipher.client.exceptions.SeedGenerationException;
import com.cipher.client.feign.AccountApiClient;
import com.cipher.client.utils.KeysUtils;
import com.cipher.client.utils.NetworkUtils;
import com.cipher.common.dto.AuthRequest;
import com.cipher.common.exception.AuthException;
import com.cipher.common.exception.CryptoException;
import com.cipher.common.exception.NetworkException;
import lombok.RequiredArgsConstructor;
import org.bitcoinj.crypto.MnemonicCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.security.*;
import java.util.Arrays;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class SeedServiceImpl {
    private final AccountApiClient accountApiClient;
    private static final Logger logger = LoggerFactory.getLogger(SeedServiceImpl.class);

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
