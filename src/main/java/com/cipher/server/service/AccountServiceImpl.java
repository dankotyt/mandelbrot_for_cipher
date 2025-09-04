package com.cipher.server.service;

import com.cipher.common.api.AccountApi;
import com.cipher.common.dto.AuthRequest;
import com.cipher.common.entity.User;
import com.cipher.common.exception.AccountAlreadyExistsException;
import com.cipher.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Реализация сервиса для операций с аккаунтами.
 * Обрабатывает бизнес-логику создания и управления пользовательскими аккаунтами.
 */
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountApi {

    private final UserRepository userRepository;

    /**
     * Создает новый аккаунт пользователя на основе предоставленных учетных данных.
     * Выполняет валидацию входных данных, проверку существования аккаунта,
     * парсинг публичного ключа и сохранение пользователя в базу данных.
     *
     * @param request запрос на создание аккаунта
     * @return ResponseEntity с HTTP статусом 200 OK при успешном создании
     * @throws IllegalArgumentException если входные данные невалидны
     * @throws AccountAlreadyExistsException если аккаунт уже существует
     * @throws RuntimeException если произошла ошибка парсинга публичного ключа
     */
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

    /**
     * Парсит публичный ключ из байтового массива в формате X.509.
     *
     * @param publicKeyBytes байтовый массив публичного ключа
     * @return объект PublicKey
     * @throws GeneralSecurityException если формат ключа невалиден или
     *                                  произошла ошибка парсинга
     */
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
