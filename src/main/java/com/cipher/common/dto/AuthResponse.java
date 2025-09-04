package com.cipher.common.dto;

/**
 * Ответ аутентификации с access token и данными пользователя.
 *
 * @param accessToken JWT токен для доступа к защищенным ресурсам
 * @param userId уникальный идентификатор пользователя
 * @param nickname отображаемое имя пользователя
 */
public record AuthResponse(String accessToken, String userId, String nickname) {
}
