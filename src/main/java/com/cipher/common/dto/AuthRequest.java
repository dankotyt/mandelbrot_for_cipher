package com.cipher.common.dto;

public record AuthRequest(
        String userId,
        String publicKey)
{}
