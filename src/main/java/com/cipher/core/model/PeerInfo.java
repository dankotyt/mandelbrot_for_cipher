package com.cipher.core.model;

import lombok.Data;
import java.net.InetAddress;
import java.time.Instant;

@Data
public class PeerInfo {
    private final InetAddress address;
    private ECDHKeyExchange ecdhKeys;
    private ConnectionStatus status;
    private Instant lastSeen;
    private Instant connectionTime;

    public PeerInfo(InetAddress address) {
        this.address = address;
        this.status = ConnectionStatus.CONNECTED;
        this.connectionTime = Instant.now();
        updateLastSeen();
    }

    public void updateLastSeen() {
        this.lastSeen = Instant.now();
    }
}

