package com.cipher.client.utils;

import com.cipher.client.service.localNetwork.KeyExchangeClient;
import com.cipher.core.model.ECDHKeyExchange;
import com.cipher.core.service.network.KeyExchangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class PeerConnector {

    private final KeyExchangeService keyExchangeService;
    private final KeyExchangeClient keyExchangeClient;

    public CompletableFuture<Boolean> connectToPeer(InetAddress peerAddress) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Проверяем доступность пира
                if (!keyExchangeClient.testConnection(peerAddress)) {
                    log.warn("Peer {} is not reachable", peerAddress.getHostAddress());
                    return false;
                }

                // Выполняем обмен ключами
                ECDHKeyExchange keys = keyExchangeService.getCurrentKeys();
                boolean success = keyExchangeClient.performKeyExchange(peerAddress, keys);
                if (success) {
                    log.info("Successfully connected to peer: {}", peerAddress.getHostAddress());
                }
                return success;

            } catch (Exception e) {
                log.error("Connection to peer {} failed: {}", peerAddress.getHostAddress(), e.getMessage());
                return false;
            }
        });
    }

    public void disconnectFromPeer(InetAddress peerAddress) {
        keyExchangeService.closeConnection(peerAddress);
        log.info("Disconnected from peer: {}", peerAddress.getHostAddress());
    }

    public Set<InetAddress> getConnectedPeers() {
        return keyExchangeService.getActiveConnections().keySet();
    }

    public boolean isConnectedTo(InetAddress peerAddress) {
        return keyExchangeService.isConnectedTo(peerAddress);
    }
}