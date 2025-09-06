package com.cipher.common.dto;

/**
 * Запрос на получение одноразового числа (nonce).
 *
 * @param userId уникальный идентификатор пользователя
 */
public record NonceRequest(String userId) {}
