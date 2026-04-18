package com.dankotyt.core.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.net.InetAddress;

@Getter
public class DeviceDiscoveredEvent extends ApplicationEvent {
    private final InetAddress deviceAddress;
    private final String deviceName;

    public DeviceDiscoveredEvent(Object source, InetAddress deviceAddress, String deviceName) {
        super(source);
        this.deviceAddress = deviceAddress;
        this.deviceName = deviceName;
    }
}
