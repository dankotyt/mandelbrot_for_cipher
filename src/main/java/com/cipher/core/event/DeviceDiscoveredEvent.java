package com.cipher.core.event;

import org.springframework.context.ApplicationEvent;

import java.net.InetAddress;

public class DeviceDiscoveredEvent extends ApplicationEvent {
    private final InetAddress deviceAddress;
    private final String deviceName;

    public DeviceDiscoveredEvent(Object source, InetAddress deviceAddress, String deviceName) {
        super(source);
        this.deviceAddress = deviceAddress;
        this.deviceName = deviceName;
    }

    public InetAddress getDeviceAddress() {
        return deviceAddress;
    }

    public String getDeviceName() {
        return deviceName;
    }
}
