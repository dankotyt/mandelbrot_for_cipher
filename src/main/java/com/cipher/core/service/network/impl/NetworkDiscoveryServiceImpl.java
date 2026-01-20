package com.cipher.core.service.network.impl;

import com.cipher.common.utils.NetworkConstants;
import com.cipher.core.event.DeviceDiscoveredEvent;
import com.cipher.core.event.DeviceLostEvent;
import com.cipher.core.model.PeerInfo;
import com.cipher.core.service.network.ConnectionManager;
import com.cipher.core.service.network.KeyExchangeService;
import com.cipher.core.service.network.NetworkDiscoveryService;
import com.cipher.client.service.localNetwork.DiscoveryServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkDiscoveryServiceImpl implements NetworkDiscoveryService {

    private final DiscoveryServer discoveryServer;
    private final KeyExchangeService keyExchangeService;
    private final ConnectionManager connectionManager;
    private final ApplicationEventPublisher eventPublisher;

    private final Set<InetAddress> discoveredPeers = ConcurrentHashMap.newKeySet();
    private final Set<InetAddress> connectedPeers = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Map<InetAddress, Long> lastSeenTimes = new ConcurrentHashMap<>();

    private static final long PEER_TIMEOUT_MS = 15000;

    private ScheduledExecutorService scheduler;
    private boolean wasShutdown = false;

    private static final long CLEANUP_INTERVAL_MS = NetworkConstants.PEER_TIMEOUT_MS / 2;

    @Override
    public void onPeerDiscovered(InetAddress peerAddress) {
        if (isOwnAddress(peerAddress)) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        lastSeenTimes.put(peerAddress, currentTime);

        if (discoveredPeers.add(peerAddress)) {
            log.info("✅ Устройство обнаружено: {}", peerAddress.getHostAddress());

            publishDeviceDiscoveredEvent(peerAddress);

            printDiscoveredPeers();
        } else {
            log.debug("Обновлено время активности устройства: {}",
                    peerAddress.getHostAddress());
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

        if (scheduler != null && !scheduler.isShutdown()) {
            try {
                scheduler.shutdown();
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        initialized.set(false);
        log.info("Network discovery service stopped (still listening for devices)");
    }

    @Override
    public void initialize() {
        if (initialized.compareAndSet(false, true) && !wasShutdown) {
            if (scheduler == null || scheduler.isShutdown() || scheduler.isTerminated()) {
                scheduler = Executors.newScheduledThreadPool(2);
                log.info("Создан новый scheduler для NetworkDiscoveryService");
            }

            startCleanupTask();
            startPeerValidationTask();

            log.info("Network discovery service initialized");
        } else if (wasShutdown) {
            log.warn("Cannot initialize - service was permanently shutdown");
        }
    }

    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupExpiredPeers();
            } catch (Exception e) {
                log.error("Error in peer cleanup task: {}", e.getMessage());
            }
        }, PEER_TIMEOUT_MS / 3, PEER_TIMEOUT_MS / 3, TimeUnit.MILLISECONDS);
    }

    private void startPeerValidationTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                validateConnectedPeers();
            } catch (Exception e) {
                log.error("Error in peer validation task: {}", e.getMessage());
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    private void cleanupExpiredPeers() {
        long now = System.currentTimeMillis();
        List<InetAddress> toRemove = new ArrayList<>();

        for (Map.Entry<InetAddress, Long> entry : lastSeenTimes.entrySet()) {
            InetAddress peer = entry.getKey();
            long lastSeen = entry.getValue();

            if (now - lastSeen > PEER_TIMEOUT_MS) {
                toRemove.add(peer);
                log.info("🚫 Устройство превысило таймаут ({} мс): {}",
                        now - lastSeen, peer.getHostAddress());
            }
        }

        for (InetAddress peer : toRemove) {
            discoveredPeers.remove(peer);
            lastSeenTimes.remove(peer);

            // Публикуем событие о потере устройства
            publishDeviceLostEvent(peer);

            log.info("Устройство удалено (таймаут): {}", peer.getHostAddress());
        }
    }

    private void validateConnectedPeers() {
        connectedPeers.forEach(peer -> {
            if (!keyExchangeService.isConnectedTo(peer)) {
                log.warn("Peer {} is in connected list but connection is lost", peer.getHostAddress());
                connectedPeers.remove(peer);
                onPeerDisconnected(peer);
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

    private boolean isOwnAddress(InetAddress address) {
        try {
            return address.isAnyLocalAddress() ||
                    address.isLoopbackAddress() ||
                    java.net.NetworkInterface.getByInetAddress(address) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void publishDeviceDiscoveredEvent(InetAddress peerAddress) {
        try {
            // Пытаемся получить имя устройства
            String deviceName = resolveDeviceName(peerAddress);

            // Публикуем событие
            DeviceDiscoveredEvent event = new DeviceDiscoveredEvent(this, peerAddress, deviceName);
            eventPublisher.publishEvent(event);

            log.debug("Событие об обнаружении устройства опубликовано: {} ({})",
                    deviceName, peerAddress.getHostAddress());

        } catch (Exception e) {
            log.warn("Ошибка публикации события: {}", e.getMessage());
            // Все равно публикуем с базовой информацией
            DeviceDiscoveredEvent event = new DeviceDiscoveredEvent(
                    this, peerAddress, peerAddress.getHostAddress());
            eventPublisher.publishEvent(event);
        }
    }

    private String resolveDeviceName(InetAddress address) {
        try {
            // Пробуем получить имя хоста
            String hostName = address.getHostName();

            // Если это IP, а не имя, возвращаем IP
            if (hostName.equals(address.getHostAddress())) {
                return "Устройство " + address.getHostAddress();
            }

            // Очищаем имя от домена (если есть)
            if (hostName.contains(".")) {
                return hostName.substring(0, hostName.indexOf('.'));
            }

            return hostName;

        } catch (Exception e) {
            return "Устройство " + address.getHostAddress();
        }
    }

    private void publishDeviceLostEvent(InetAddress peerAddress) {
        DeviceLostEvent event = new DeviceLostEvent(this, peerAddress);
        eventPublisher.publishEvent(event);
        log.debug("Событие о потере устройства опубликовано: {}",
                peerAddress.getHostAddress());
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public void permanentShutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        discoveredPeers.clear();
        lastSeenTimes.clear();
        connectedPeers.clear();

        wasShutdown = true;
        initialized.set(false);

        log.info("Network discovery service permanently shutdown");
    }
}
