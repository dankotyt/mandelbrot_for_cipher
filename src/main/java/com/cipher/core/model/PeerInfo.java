package com.cipher.core.model;

import lombok.Data;
import java.net.InetAddress;
import java.time.Instant;

@Data
public class PeerInfo {
    private final InetAddress address;
    private ConnectionStatus status;
    private Instant lastSeen;
    private Instant connectionTime;
    private long lastKeyExchangeTime;
    private ECDHKeyExchange ecdhKeys;

    public PeerInfo(InetAddress address) {
        this.address = address;
        this.status = ConnectionStatus.CONNECTED;
        this.connectionTime = Instant.now();
        this.lastKeyExchangeTime = System.currentTimeMillis();
        updateLastSeen();
    }

    public void updateLastSeen() {
        this.lastSeen = Instant.now();
    }

    public void updateKeyExchangeTime() {
        this.lastKeyExchangeTime = System.currentTimeMillis();
    }

    public boolean isExpired(long timeoutMs) {
        return lastSeen.plusMillis(timeoutMs).isBefore(Instant.now());
    }

    public boolean isKeyExchangeExpired(long timeoutMs) {
        return (System.currentTimeMillis() - lastKeyExchangeTime) > timeoutMs;
    }
}

