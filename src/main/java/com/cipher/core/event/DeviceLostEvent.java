package com.cipher.core.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.net.InetAddress;

@Getter
public class DeviceLostEvent extends ApplicationEvent {
    private final InetAddress deviceAddress;

    public DeviceLostEvent(Object source, InetAddress deviceAddress) {
        super(source);
        this.deviceAddress = deviceAddress;
    }
}
