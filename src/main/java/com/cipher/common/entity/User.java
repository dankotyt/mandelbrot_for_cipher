package com.cipher.common.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.security.PublicKey;
import java.util.Base64;
import java.util.Objects;

/**
 * Сущность пользователя системы.
 * Представляет зарегистрированного пользователя с криптографическими ключами.
 */
@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    /**
     * Уникальный идентификатор пользователя.
     * Используется как первичный ключ в таблице.
     */
    @Id
    @Column(nullable = false, unique = true)
    private String userId;

    /**
     * Публичный ключ пользователя в base64 кодировке.
     * Используется для верификации подписей.
     */
    @Column(nullable = false)
    private String publicKey;

    /**
     * Отображаемое имя пользователя.
     * Генерируется автоматически на основе userId.
     */
    @Column(nullable = false, unique = true)
    private String nickname;

    /**
     * Возвращает публичный ключ в виде байтового массива.
     *
     * @return декодированный публичный ключ
     */
    public byte[] getPublicKeyBytes() {
        return Base64.getDecoder().decode(publicKey);
    }

    /**
     * Приватный конструктор для создания сущности User.
     *
     * @param userId уникальный идентификатор пользователя
     * @param publicKey публичный ключ в виде байтового массива
     * @param nickname отображаемое имя пользователя
     */
    private User(String userId, byte[] publicKey, String nickname) {
        this.userId = Objects.requireNonNull(userId, "UserId must not be null");
        this.publicKey = Base64.getEncoder().encodeToString(publicKey);
        this.nickname = Objects.requireNonNull(nickname, "Nickname must not be null");
    }

    /**
     * Фабричный метод для создания пользователя из криптографических ключей.
     *
     * @param userId уникальный идентификатор пользователя
     * @param publicKey публичный ключ пользователя
     * @return созданная сущность User
     * @throws IllegalArgumentException если передан неподдерживаемый тип ключа
     *                                  или невалидный userId
     */
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
