package com.cipher.online.service;

import com.cipher.common.api.AccountApi;
import com.cipher.common.dto.AuthRequest;
import com.cipher.common.entity.User;
import com.cipher.common.exception.AccountAlreadyExistsException;
import com.cipher.online.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountApi {


    private final UserRepository userRepository;

    @Override
    public ResponseEntity<Void> generateAccount(AuthRequest request) {
        if (request.userId() == null || request.userId().isBlank()) {
            throw new IllegalArgumentException("User ID cannot be empty");
        }

        if (request.publicKey() == null || request.publicKey().isBlank()) {
            throw new IllegalArgumentException("Public key cannot be empty");
        }
        if (userRepository.existsByUserId(request.userId())) {
            throw new AccountAlreadyExistsException("Account already exists!");
        }
        try {
            byte[] publicKeyBytes = Base64.getDecoder().decode(request.publicKey());

            PublicKey publicKey = parsePublicKey(publicKeyBytes);

            User user = User.createFromCryptoKeys(request.userId(), publicKey);

            userRepository.save(user);
            return ResponseEntity.ok().build();

        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to parse public key during registration", e);
        }
    }

    private PublicKey parsePublicKey(byte[] publicKeyBytes) throws GeneralSecurityException {
        try {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("EdDSA");
            return keyFactory.generatePublic(keySpec);
        } catch (Exception e) {
            throw new GeneralSecurityException("Invalid public key format", e);
        }
    }
}
