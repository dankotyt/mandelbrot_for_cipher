package com.cipher.client.service;

import com.cipher.client.feign.AccountApiClient;
import com.cipher.client.utils.KeysUtils;
import com.cipher.common.dto.AuthRequest;
import lombok.RequiredArgsConstructor;
import org.bitcoinj.crypto.MnemonicCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.*;
import java.util.Arrays;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class SeedServiceImpl {
    private final AccountApiClient accountApiClient;
    private static final Logger logger = LoggerFactory.getLogger(SeedServiceImpl.class);

    public String generateAccount() throws NoSuchAlgorithmException {
        List<String> words = null;
        try {
            words = generatedWordsForSeed();
            KeysUtils.CryptoKeys keys = KeysUtils.createKeysFromWords(words);

            logger.info("PublicKey algorithm: " + keys.publicKey().getAlgorithm());
            logger.info("PublicKey format: " + keys.publicKey().getFormat());
            logger.info("PublicKey encoded length: " + keys.publicKey().getEncoded().length);

            String publicKeyBase64 = KeysUtils.getPublicKeyBase64(keys.publicKey());

            AuthRequest request = new AuthRequest(
                    keys.userId(), publicKeyBase64
            );

            accountApiClient.generateAccount(request);

            return String.join(" ", words);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } finally {
            assert words != null;
            words.clear();
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
        } finally {
            assert entropy != null;
            Arrays.fill(entropy, (byte) 0);
        }
    }
}
