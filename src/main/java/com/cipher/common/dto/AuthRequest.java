package com.cipher.common.dto;

/**
 * Запрос на создание аккаунта.
 * Содержит идентификатор пользователя и публичный ключ в base64 кодировке.
 *
 * @param userId уникальный идентификатор пользователя
 * @param publicKey публичный ключ в формате base64
 */
public record AuthRequest(
        String userId,
        String publicKey)
{}
