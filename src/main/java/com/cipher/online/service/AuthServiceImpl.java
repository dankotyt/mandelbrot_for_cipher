package com.cipher.online.service;

import com.cipher.common.dto.AuthResponse;
import com.cipher.common.dto.LoginRequest;
import com.cipher.common.dto.NonceRequest;
import com.cipher.common.dto.NonceResponse;
import com.cipher.common.entity.User;
import com.cipher.common.exception.SeedNotFoundException;
import com.cipher.online.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class AuthServiceImpl {
    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();
    private final RedissonClient redissonClient;

    private static final String NONCE_KEY_PREFIX = "auth_nonce:";

    public NonceResponse requestNonce(NonceRequest request) {
        String userId = request.userId();
        if (!userRepository.existsByUserId(userId)) {
            throw new SeedNotFoundException("Account not found!");
        }
        byte[] nonceBytes = new byte[16];
        secureRandom.nextBytes(nonceBytes);
        String nonce = Base64.getEncoder().encodeToString(nonceBytes);

        String redisKey = NONCE_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(redisKey, nonce, 15, TimeUnit.SECONDS);

        return new NonceResponse(nonce);
    }

    public AuthResponse login(LoginRequest request) {
        String userId = request.userId();
        String signatureBase64 = request.signature();

        String redisKey = NONCE_KEY_PREFIX + userId;

        logger.info("login by userId: {}, signature: {}", userId, signatureBase64);
        RBucket<String> nonceBucket = redissonClient.getBucket(
                redisKey,
                StringCodec.INSTANCE
        );

        String nonce = nonceBucket.getAndDelete();
        logger.info("Nonce retrieved and deleted for key: {}", redisKey);

        if (nonce == null) {
            logger.warn("Nonce not found for user: {}", userId);
            throw new SecurityException("Nonce expired or not found!");
        }

        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new SecurityException("User not found!"));

        // ДЕБАГ: Проверяем что хранится в БД
        byte[] publicKeyBytesFromDB = user.getPublicKeyBytes();
        logger.debug("PublicKey from DB - Length: {}, Hex: {}, Base64: {}",
                publicKeyBytesFromDB.length,
                bytesToHex(publicKeyBytesFromDB),
                Base64.getEncoder().encodeToString(publicKeyBytesFromDB));
        try {
            Signature verifier = Signature.getInstance("EdDSA");

            byte[] x509Bytes = user.getPublicKeyBytes();

            logger.debug("X.509 key from DB - Length: {}, Base64: {}",
                    x509Bytes.length,
                    Base64.getEncoder().encodeToString(x509Bytes));

            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(x509Bytes);
            KeyFactory keyFactory = KeyFactory.getInstance("EdDSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            verifier.initVerify(publicKey);
            verifier.update(nonce.getBytes(StandardCharsets.UTF_8));

            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
            boolean isValid = verifier.verify(signatureBytes);

            if (isValid) {
                String authToken = "generated_jwt_token_here";
                return new AuthResponse(authToken, user.getUserId(), user.getNickname());
            } else {
                throw new SecurityException("Invalid signature");
            }
        } catch (GeneralSecurityException e) {
            logger.error("Signature verification failed. X.509 key length: {}",
                    user.getPublicKeyBytes().length, e);
            throw new SecurityException("Signature verification failed", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
