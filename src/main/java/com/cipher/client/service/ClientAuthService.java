package com.cipher.client.service;

import com.cipher.client.feign.AuthApiClient;
import com.cipher.client.utils.KeysUtils;
import com.cipher.common.dto.AuthResponse;
import com.cipher.common.dto.LoginRequest;
import com.cipher.common.dto.NonceRequest;
import com.cipher.common.dto.NonceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClientAuthService {

    private final AuthApiClient authApiClient;

    public String login(List<String> words) throws Exception {
        KeysUtils.CryptoKeys keys = KeysUtils.createKeysFromWords(words);

        NonceResponse nonceResponse = authApiClient.requestNonce(new NonceRequest(keys.userId()));

        byte[] signature = signData(nonceResponse.nonce().getBytes(StandardCharsets.UTF_8), keys.privateKey());
        String signatureBase64 = Base64.getEncoder().encodeToString(signature);

        AuthResponse authResponse = authApiClient.login(new LoginRequest(keys.userId(), signatureBase64));

        return authResponse.accessToken();
    }

    private byte[] signData(byte[] data, PrivateKey privateKey) throws GeneralSecurityException {
        Signature signer = Signature.getInstance("EdDSA");

        signer.initSign(privateKey);

        signer.update(data);

        return signer.sign();
    }
}
