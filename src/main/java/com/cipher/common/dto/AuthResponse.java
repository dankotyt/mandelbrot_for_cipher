package com.cipher.common.dto;

public record AuthResponse(String accessToken, String userId, String nickname) {
}
