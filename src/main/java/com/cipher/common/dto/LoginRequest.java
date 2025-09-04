package com.cipher.common.dto;

/**
 * Запрос на вход в систему.
 * Содержит идентификатор пользователя и подпись nonce.
 *
 * @param userId уникальный идентификатор пользователя
 * @param signature подпись nonce в формате base64
 */
public record LoginRequest(String userId, String signature) {}
