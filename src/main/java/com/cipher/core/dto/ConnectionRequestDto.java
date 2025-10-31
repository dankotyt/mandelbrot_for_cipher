package com.cipher.core.dto;

import java.time.LocalDateTime;

public record ConnectionRequestDto(
        String fromDeviceName,
        String fromDeviceIp,
        String toDeviceName,
        String toDeviceIp,
        LocalDateTime timestamp,
        RequestStatus status
) {
    public enum RequestStatus {
        PENDING, ACCEPTED, REJECTED, EXPIRED
    }
}
