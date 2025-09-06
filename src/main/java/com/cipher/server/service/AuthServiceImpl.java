package com.cipher.server.service;

import com.cipher.common.api.AuthApi;
import com.cipher.common.dto.AuthResponse;
import com.cipher.common.dto.LoginRequest;
import com.cipher.common.dto.NonceRequest;
import com.cipher.common.dto.NonceResponse;
import com.cipher.common.entity.User;
import com.cipher.common.exception.SeedNotFoundException;
import com.cipher.common.utils.SecureRandomUtils;
import com.cipher.server.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Сервис аутентификации и авторизации пользователей.
 * Обеспечивает генерацию nonce, верификацию подписей и выдачу access token.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthApi {
    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedissonClient redissonClient;
    private final KeyFactory keyFactory;

    private static final String NONCE_KEY_PREFIX = "auth_nonce:";
    private static final int NONCE_BYTE_LENGTH = 16;
    private static final int NONCE_TTL_SECONDS = 15;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * Инициализирует криптографические провайдеры при запуске приложения.
     * Выполняет предварительную загрузку KeyFactory для проверки доступности
     * криптографических алгоритмов.
     *
     * @throws NoSuchAlgorithmException если алгоритм EdDSA недоступен
     * @throws NoSuchProviderException если провайдер BC недоступен
     *
     * @implNote Метод выполняется после создания бинов, но до начала обработки запросов
     * @see PostConstruct
     */
    @PostConstruct
    public void init() throws NoSuchAlgorithmException, NoSuchProviderException {
        KeyFactory.getInstance("EdDSA", "BC");
    }

    /**
     * Генерирует и возвращает одноразовое число (nonce) для аутентификации.
     * Сохраняет nonce в Redis с временем жизни 15 секунд.
     *
     * @param request запрос nonce с идентификатором пользователя
     * @return ответ со сгенерированным nonce
     * @throws SeedNotFoundException если пользователь не найден
     */
    @Override
    public NonceResponse requestNonce(NonceRequest request) {
        final String userId = request.userId();

        if (!userRepository.existsByUserId(userId)) {
            throw new SeedNotFoundException("Account not found!");
        }

        try {
            byte[] nonceBytes = SecureRandomUtils.generateRandomBytes(NONCE_BYTE_LENGTH);
            String nonce = Base64.getEncoder().encodeToString(nonceBytes);
            String redisKey = NONCE_KEY_PREFIX + userId;

            redisTemplate.opsForValue().set(redisKey, nonce, NONCE_TTL_SECONDS, TimeUnit.SECONDS);

            return new NonceResponse(nonce);
        } finally {
            SecureRandomUtils.cleanUp();
        }
    }

    /**
     * Выполняет аутентификацию пользователя на основе подписи nonce.
     * Проверяет валидность подписи с использованием публичного ключа пользователя.
     *
     * @param request запрос на вход с идентификатором пользователя и подписью
     * @return ответ аутентификации с access token и данными пользователя
     * @throws SecurityException если nonce истек, подпись невалидна или пользователь не найден
     */
    @Override
    public AuthResponse login(LoginRequest request) {
        final String userId = request.userId();
        final String signatureBase64 = request.signature();
        final String redisKey = NONCE_KEY_PREFIX + userId;

        try {
            String nonce = getAndDeleteNonce(redisKey);

            if (nonce == null) {
                logger.warn("Nonce not found for user: {}", userId);
                throw new SecurityException("Nonce expired or not found!");
            }

            User user = userRepository.findByUserId(userId)
                    .orElseThrow(() -> new SecurityException("User not found!"));

            boolean isValid = verifySignature(user.getPublicKeyBytes(), nonce, signatureBase64);

            if (!isValid) {
                logger.warn("Invalid signature for user: {}", userId);
                throw new SecurityException("Invalid signature");
            }

            return new AuthResponse(user.getUserId(), user.getNickname());

        } finally {
            SecureRandomUtils.cleanUp();
        }
    }

    /**
     * Атомарно извлекает и удаляет nonce значение из Redis.
     * Операция выполняется атомарно для предотвращения race condition.
     *
     * @param key ключ для доступа к nonce в Redis
     * @return значение nonce или null если ключ не существует или истек срок действия
     *
     * @implSpec Использует Redisson client для атомарных операций с Redis
     * @see RBucket#getAndDelete()
     */
    private String getAndDeleteNonce(String key) {
        RBucket<String> nonceBucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
        return nonceBucket.getAndDelete();
    }

    /**
     * Проверяет цифровую подпись с использованием публичного ключа.
     * Выполняет верификацию подписи для предоставленных данных.
     *
     * @param publicKeyBytes байтовое представление публичного ключа в X509 формате
     * @param nonce одноразовое число (nonce) которое было подписано
     * @param signatureBase64 подпись в Base64 кодировке для верификации
     * @return true если подпись валидна, false в противном случае
     * @throws SecurityException если произошла ошибка во время верификации
     *
     * @implNote Метод создает новый экземпляр Signature для каждого вызова
     *           так как Signature не является потокобезопасным.
     * @implSpec После выполнения операции сбрасывает состояние Signature
     *           для возможного повторного использования экземпляра.
     */
    private boolean verifySignature(byte[] publicKeyBytes, String nonce, String signatureBase64) {
        Signature verifier = createSignature();

        try {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            verifier.initVerify(publicKey);
            verifier.update(nonce.getBytes(StandardCharsets.UTF_8));

            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
            return verifier.verify(signatureBytes);

        } catch (GeneralSecurityException e) {
            logger.error("Signature verification failed", e);
            throw new SecurityException("Signature verification failed", e);
        } finally {
            try { verifier.initVerify((PublicKey) null); } catch (Exception ignored) {}
        }
    }

    /**
     * Создает новый экземпляр Signature для каждого запроса
     * Signature НЕ потокобезопасен, поэтому нельзя использовать один экземпляр!
     */
    private Signature createSignature() {
        try {
            return Signature.getInstance("EdDSA");
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to create Signature instance", e);
            throw new IllegalStateException("EdDSA signature not available", e);
        }
    }
}
