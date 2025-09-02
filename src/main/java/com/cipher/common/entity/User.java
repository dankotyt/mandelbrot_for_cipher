package com.cipher.common.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.security.PublicKey;
import java.util.Base64;
import java.util.Objects;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @Id
    @Column(nullable = false, unique = true)
    private String userId;

    @Column(nullable = false)
    private String publicKey;

    @Column(nullable = false, unique = true)
    private String nickname;

    public byte[] getPublicKeyBytes() {
        return Base64.getDecoder().decode(publicKey);
    }

    private User(String userId, byte[] publicKey, String nickname) {
        this.userId = Objects.requireNonNull(userId, "UserId must not be null");
        this.publicKey = Base64.getEncoder().encodeToString(publicKey);
        this.nickname = Objects.requireNonNull(nickname, "Nickname must not be null");
    }

    public static User createFromCryptoKeys(String userId, PublicKey publicKey) {
        String algorithm = publicKey.getAlgorithm();
        if (!"Ed25519".equals(algorithm) && !"EdDSA".equals(algorithm)) {
            throw new IllegalArgumentException("Only Ed25519/EdDSA keys are supported. Got: " + algorithm);
        }

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("UserId must not be null or blank");
        }

        String nickname = "User_" + userId.substring(0, 5);

        byte[] x509Encoded = publicKey.getEncoded();

        return new User(userId, x509Encoded, nickname);
    }
}
