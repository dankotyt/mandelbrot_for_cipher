package com.cipher.core.dto.connection;

import java.net.InetAddress;
import java.time.LocalDateTime;

public record ConnectionRequestDTO(
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

    public InetAddress getFromDeviceIpAsInetAddress() {
        try {
            return InetAddress.getByName(fromDeviceIp);
        } catch (Exception e) {
            throw new RuntimeException("Invalid IP address: " + fromDeviceIp, e);
        }
    }

    public InetAddress getToDeviceIpAsInetAddress() {
        try {
            return InetAddress.getByName(toDeviceIp);
        } catch (Exception e) {
            throw new RuntimeException("Invalid IP address: " + toDeviceIp, e);
        }
    }
}
