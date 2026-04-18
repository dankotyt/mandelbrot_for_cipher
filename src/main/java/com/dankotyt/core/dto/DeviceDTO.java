package com.dankotyt.core.dto;

public record DeviceDTO(String name, String ip) {
    @Override
    public String toString() {
        return name() + " - " + ip();
    }
}
