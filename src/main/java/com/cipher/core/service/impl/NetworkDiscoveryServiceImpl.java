package com.cipher.core.service.impl;

import com.cipher.client.DiscoveryClient;
import com.cipher.client.PeerConnector;
import com.cipher.common.NetworkConstants;
import com.cipher.core.model.PeerInfo;
import com.cipher.core.service.ConnectionManager;
import com.cipher.core.service.NetworkDiscoveryService;
import com.cipher.server.DiscoveryServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkDiscoveryServiceImpl implements NetworkDiscoveryService {

    private final DiscoveryServer discoveryServer;
    private final PeerConnector peerConnector;
    private final ConnectionManager connectionManager;

    private final Set<InetAddress> discoveredPeers = ConcurrentHashMap.newKeySet();
    private final Set<InetAddress> connectedPeers = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private static final long CLEANUP_INTERVAL_MS = NetworkConstants.PEER_TIMEOUT_MS / 2;

    @Override
    public void onPeerDiscovered(InetAddress peerAddress) {
        if (discoveredPeers.add(peerAddress)) {
            log.info("New peer discovered: {}", peerAddress.getHostAddress());
            printDiscoveredPeers();
        }
    }

    @Override
    public void onPeerConnected(InetAddress peerAddress) {
        if (connectedPeers.add(peerAddress)) {
            log.info("Peer connected: {}", peerAddress.getHostAddress());
            printConnectedPeers();
        }
    }

    @Override
    public void onPeerDisconnected(InetAddress peerAddress) {
        if (connectedPeers.remove(peerAddress)) {
            log.info("Peer disconnected: {}", peerAddress.getHostAddress());
            printConnectedPeers();
        }
    }

    @Override
    public Set<InetAddress> getDiscoveredPeers() {
        return Set.copyOf(discoveredPeers);
    }

    @Override
    public Set<InetAddress> getConnectedPeers() {
        return Set.copyOf(connectedPeers);
    }

    @Override
    public void broadcastPresence() {
        // DiscoveryServer уже постоянно рассылает сообщения
        log.debug("Presence broadcasting is active");
    }

    @Override
    public void stopDiscovery() {
        if (initialized.compareAndSet(true, false)) {
            discoveryServer.stop();
            scheduler.shutdown();
            discoveredPeers.clear();
            connectedPeers.clear();
            log.info("Network discovery completely stopped");
        }
    }

    @Override
    public void initialize() {
        if (initialized.compareAndSet(false, true)) {
            // Запускаем сервер для анонсирования
            discoveryServer.start();

            // Запускаем периодические задачи
            startCleanupTask();
            startPeerValidationTask();

            log.info("Network discovery service initialized");
        }
    }

    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupExpiredPeers();
            } catch (Exception e) {
                log.error("Error in peer cleanup task: {}", e.getMessage());
            }
        }, CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void startPeerValidationTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                validateConnectedPeers();
            } catch (Exception e) {
                log.error("Error in peer validation task: {}", e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void cleanupExpiredPeers() {
        try {
            // Используем ConnectionManager для очистки устаревших пиров
            connectionManager.cleanupExpiredPeers(NetworkConstants.PEER_TIMEOUT_MS);

            // Синхронизируем наш локальный список с ConnectionManager
            Map<InetAddress, PeerInfo> currentConnected = connectionManager.getConnectedPeers();

            // Обновляем connectedPeers
            connectedPeers.clear();
            connectedPeers.addAll(currentConnected.keySet());
        } catch (Exception e) {
            log.error("Error in cleanupExpiredPeers: {}", e.getMessage());
        }
    }

    private void validateConnectedPeers() {
        // Проверяем, что подключенные пиры все еще доступны
        connectedPeers.forEach(peer -> {
            if (!peerConnector.isConnectedTo(peer)) {
                log.warn("Peer {} is in connected list but connection is lost", peer.getHostAddress());
                connectedPeers.remove(peer);
            }
        });
    }

    private void printDiscoveredPeers() {
        if (discoveredPeers.isEmpty()) {
            log.info("Discovered peers: none");
        } else {
            StringBuilder sb = new StringBuilder("Discovered peers: ");
            discoveredPeers.forEach(addr -> {
                String status = connectedPeers.contains(addr) ? "[connected]" : "[available]";
                sb.append(addr.getHostAddress()).append(status).append(" ");
            });
            log.info(sb.toString());
        }
    }

    private void printConnectedPeers() {
        if (connectedPeers.isEmpty()) {
            log.info("Connected peers: none");
        } else {
            StringBuilder sb = new StringBuilder("Connected peers: ");
            connectedPeers.forEach(addr ->
                    sb.append(addr.getHostAddress()).append(" "));
            log.info(sb.toString());
        }
    }

    public void manuallyAddPeer(InetAddress peerAddress) {
        if (discoveredPeers.add(peerAddress)) {
            log.info("Manually added peer: {}", peerAddress.getHostAddress());
            printDiscoveredPeers();
        }
    }

    public void manuallyRemovePeer(InetAddress peerAddress) {
        if (discoveredPeers.remove(peerAddress)) {
            log.info("Manually removed peer: {}", peerAddress.getHostAddress());
            printDiscoveredPeers();
        }
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public void shutdown() {
        stopDiscovery();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Network discovery service shutdown complete");
    }
}
