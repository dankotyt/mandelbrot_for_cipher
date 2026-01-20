package com.cipher.core.event;

import org.springframework.context.ApplicationEvent;

import java.net.InetAddress;

public class DeviceLostEvent extends ApplicationEvent {
    private final InetAddress deviceAddress;

    public DeviceLostEvent(Object source, InetAddress deviceAddress) {
        super(source);
        this.deviceAddress = deviceAddress;
    }

    public InetAddress getDeviceAddress() {
        return deviceAddress;
    }
}
