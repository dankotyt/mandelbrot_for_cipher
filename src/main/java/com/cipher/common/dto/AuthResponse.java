package com.cipher.common.dto;

/**
 * Ответ аутентификации с access token и данными пользователя.
 *
 * @param userId уникальный идентификатор пользователя
 * @param nickname отображаемое имя пользователя
 */
public record AuthResponse(String userId, String nickname) {
}
