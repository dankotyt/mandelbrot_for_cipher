package com.cipher.core.service.network.impl;

import com.cipher.client.service.localNetwork.DiscoveryServer;
import com.cipher.core.model.PeerInfo;
import com.cipher.core.service.network.ConnectionManager;
import com.cipher.core.service.network.KeyExchangeService;
import com.cipher.core.service.network.NetworkDiscoveryService;
import com.cipher.core.utils.NetworkManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
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

    private final Set<InetAddress> discoveredPeers = ConcurrentHashMap.newKeySet();
    private final Set<InetAddress> connectedPeers = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    private NetworkManager.DevicesUpdateCallback devicesCallback;

    private static final long CLEANUP_INTERVAL_MS = 30000; // 30 секунд

    @Override
    public void onPeerDiscovered(InetAddress peerAddress) {
        if (discoveredPeers.add(peerAddress)) {
            log.info("🆕 Новое устройство обнаружено: {}", peerAddress.getHostAddress());

            // ✅ ТОЛЬКО обнаружение, без автоматического подключения
            printDiscoveredPeers();

            // ✅ Уведомляем UI о новом устройстве
            notifyDeviceDiscovered(peerAddress);
        }
    }

    private void notifyDeviceDiscovered(InetAddress peerAddress) {
        if (devicesCallback != null) {
            devicesCallback.onDeviceDiscovered(peerAddress);
        } else {
            log.debug("📢 Новое устройство: {} (UI callback не установлен)", peerAddress.getHostAddress());
        }
    }

    @Override
    public void onPeerConnected(InetAddress peerAddress) {
        if (connectedPeers.add(peerAddress)) {
            log.info("🔗 Устройство подключено: {}", peerAddress.getHostAddress());
            printConnectedPeers();
        }
    }

    @Override
    public void onPeerDisconnected(InetAddress peerAddress) {
        if (connectedPeers.remove(peerAddress)) {
            log.info("🔌 Устройство отключено: {}", peerAddress.getHostAddress());
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
        log.debug("📢 Рассылка присутствия активна");
    }

    @Override
    public void stopDiscovery() {
        if (initialized.compareAndSet(true, false)) {
            discoveryServer.stop();
            scheduler.shutdown();
            discoveredPeers.clear();
            connectedPeers.clear();
            log.info("🛑 Обнаружение устройств полностью остановлено");
        }
    }

    @Override
    public void initialize() {
        if (initialized.compareAndSet(false, true)) {
            discoveryServer.start();
            startCleanupTask();
            startPeerValidationTask();
            log.info("✅ Сервис обнаружения устройств инициализирован");
        }
    }

    /**
     * Ручное подключение к обнаруженному устройству
     */
    public CompletableFuture<Boolean> connectToDiscoveredPeer(InetAddress peerAddress) {
        return keyExchangeService.performKeyExchangeAsync(peerAddress)
                .thenApply(success -> {
                    if (success) {
                        onPeerConnected(peerAddress);
                    }
                    return success;
                });
    }

    /**
     * Отключение от устройства
     */
    public void disconnectFromPeer(InetAddress peerAddress) {
        keyExchangeService.closeConnection(peerAddress);
        onPeerDisconnected(peerAddress);
    }

    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupExpiredPeers();
            } catch (Exception e) {
                log.error("❌ Ошибка в задаче очистки: {}", e.getMessage());
            }
        }, CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void startPeerValidationTask() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                validateConnectedPeers();
            } catch (Exception e) {
                log.error("❌ Ошибка в задаче валидации: {}", e.getMessage());
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void cleanupExpiredPeers() {
        try {
            connectionManager.cleanupExpiredPeers(60000); // 60 секунд таймаут

            Map<InetAddress, PeerInfo> currentConnected = connectionManager.getConnectedPeers();
            connectedPeers.clear();
            connectedPeers.addAll(currentConnected.keySet());

            log.debug("🧹 Очистка устаревших подключений завершена");

        } catch (Exception e) {
            log.error("❌ Ошибка при очистке устаревших пиров: {}", e.getMessage());
        }
    }

    private void validateConnectedPeers() {
        connectedPeers.forEach(peer -> {
            if (!connectionManager.isConnectedTo(peer)) {
                log.warn("⚠️ Устройство {} в списке подключенных, но соединение потеряно",
                        peer.getHostAddress());
                connectedPeers.remove(peer);
                onPeerDisconnected(peer);
            }
        });
    }

    private void printDiscoveredPeers() {
        if (discoveredPeers.isEmpty()) {
            log.info("📋 Обнаруженные устройства: нет");
        } else {
            StringBuilder sb = new StringBuilder("📋 Обнаруженные устройства: ");
            discoveredPeers.forEach(addr -> {
                String status = connectedPeers.contains(addr) ? "[🔗]" : "[🟡]";
                sb.append(addr.getHostAddress()).append(status).append(" ");
            });
            log.info(sb.toString());
        }
    }

    private void printConnectedPeers() {
        if (connectedPeers.isEmpty()) {
            log.info("🔗 Подключенные устройства: нет");
        } else {
            StringBuilder sb = new StringBuilder("🔗 Подключенные устройства: ");
            connectedPeers.forEach(addr -> sb.append(addr.getHostAddress()).append(" "));
            log.info(sb.toString());
        }
    }

    public void manuallyAddPeer(InetAddress peerAddress) {
        if (discoveredPeers.add(peerAddress)) {
            log.info("👤 Ручное добавление устройства: {}", peerAddress.getHostAddress());
            printDiscoveredPeers();
        }
    }

    public void manuallyRemovePeer(InetAddress peerAddress) {
        if (discoveredPeers.remove(peerAddress)) {
            log.info("🗑️ Ручное удаление устройства: {}", peerAddress.getHostAddress());
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
        log.info("✅ Сервис обнаружения устройств выключен");
    }
}