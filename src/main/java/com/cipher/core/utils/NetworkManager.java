package com.cipher.core.utils;

import com.cipher.core.service.network.ConnectionManager;
import com.cipher.core.service.network.KeyExchangeService;
import com.cipher.core.service.network.NetworkDiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class NetworkManager {

    private final NetworkDiscoveryService networkDiscoveryService;
    private final KeyExchangeService keyExchangeService;
    private final ConnectionManager connectionManager;

    private final Map<InetAddress, Long> discoveredDevices = new ConcurrentHashMap<>();
    private final ScheduledExecutorService updateExecutor = Executors.newSingleThreadScheduledExecutor();
    private DevicesUpdateCallback devicesCallback;
    private boolean servicesRunning = false;

    public interface DevicesUpdateCallback {
        void onDevicesUpdated(Map<InetAddress, Long> devices);
        void onConnectionStatusChanged(String status);
        void onError(String error);
        void onDeviceDiscovered(InetAddress address);
        void onDeviceLost(InetAddress address);
    }

    public void startNetworkServices(DevicesUpdateCallback callback) {
        this.devicesCallback = callback;

        try {
            connectionManager.start();

            networkDiscoveryService.initialize();

            startDevicesUpdateTask();

            servicesRunning = true;

            log.info("✅ Сетевые службы запущены");
            callback.onConnectionStatusChanged("Сетевые службы запущены");

        } catch (Exception e) {
            log.error("❌ Ошибка запуска сетевых служб: {}", e.getMessage());
            callback.onError("Ошибка запуска сетевых служб: " + e.getMessage());
        }
    }

    public void stopNetworkServices() {
        servicesRunning = false;

        try {
            updateExecutor.shutdown();
            connectionManager.stop();
            networkDiscoveryService.stopDiscovery();

            log.info("🛑 Сетевые службы остановлены");
            if (devicesCallback != null) {
                devicesCallback.onConnectionStatusChanged("Сетевые службы остановлены");
            }

        } catch (Exception e) {
            log.error("❌ Ошибка остановки сетевых служб: {}", e.getMessage());
        }
    }

    public Map<InetAddress, Long> getDiscoveredDevices() {
        Set<InetAddress> currentPeers = networkDiscoveryService.getDiscoveredPeers();
        long currentTime = System.currentTimeMillis();

        discoveredDevices.keySet().removeIf(peer -> !currentPeers.contains(peer));
        currentPeers.forEach(peer -> discoveredDevices.put(peer, currentTime));

        return new ConcurrentHashMap<>(discoveredDevices);
    }

    public void connectToDevice(InetAddress address) {
        if (devicesCallback == null) {
            log.warn("⚠️ Нет callback для событий подключения");
            return;
        }

        devicesCallback.onConnectionStatusChanged("Подключение к " + address.getHostAddress() + "...");

        keyExchangeService.performKeyExchangeAsync(address)
                .whenComplete((success, throwable) -> {
                    if (throwable != null) {
                        String error = "❌ Ошибка подключения: " + throwable.getMessage();
                        log.error(error);
                        devicesCallback.onError(error);
                        devicesCallback.onConnectionStatusChanged("Ошибка подключения");
                    } else if (success) {
                        String status = "✅ Успешно подключено к " + address.getHostAddress();
                        log.info(status);
                        devicesCallback.onConnectionStatusChanged(status);

                        activateChatForPeer(address);
                    } else {
                        String error = "❌ Не удалось подключиться к " + address.getHostAddress();
                        log.error(error);
                        devicesCallback.onError(error);
                        devicesCallback.onConnectionStatusChanged("Подключение не удалось");
                    }
                });
    }

    public void disconnectFromDevice(InetAddress address) {
        try {
            keyExchangeService.closeConnection(address);
            connectionManager.removePeerSocket(address);

            String status = "🔌 Отключено от " + address.getHostAddress();
            log.info(status);
            if (devicesCallback != null) {
                devicesCallback.onConnectionStatusChanged(status);
            }

        } catch (Exception e) {
            log.error("❌ Ошибка отключения от {}: {}", address.getHostAddress(), e.getMessage());
        }
    }

    public void refreshConnections() {
        getDiscoveredDevices();
        if (devicesCallback != null) {
            devicesCallback.onConnectionStatusChanged("🔄 Список подключений обновлен");
        }
    }

    /**
     * Активация чата для подключенного пира
     */
    private void activateChatForPeer(InetAddress address) {
        try {
            // Здесь можно добавить логику активации чата
            log.info("💬 Чат активирован для: {}", address.getHostAddress());

        } catch (Exception e) {
            log.error("❌ Ошибка активации чата для {}: {}", address.getHostAddress(), e.getMessage());
        }
    }

    private void startDevicesUpdateTask() {
        updateExecutor.scheduleAtFixedRate(() -> {
            if (!servicesRunning) return;

            try {
                Map<InetAddress, Long> previousDevices = new ConcurrentHashMap<>(discoveredDevices);
                Map<InetAddress, Long> currentDevices = getDiscoveredDevices();

                // Определяем новые и пропавшие устройства
                for (InetAddress address : currentDevices.keySet()) {
                    if (!previousDevices.containsKey(address) && devicesCallback != null) {
                        devicesCallback.onDeviceDiscovered(address);
                    }
                }

                for (InetAddress address : previousDevices.keySet()) {
                    if (!currentDevices.containsKey(address) && devicesCallback != null) {
                        devicesCallback.onDeviceLost(address);
                    }
                }

                // Уведомляем callback об обновлении
                if (devicesCallback != null) {
                    devicesCallback.onDevicesUpdated(currentDevices);
                }

            } catch (Exception e) {
                log.error("❌ Ошибка в задаче обновления устройств: {}", e.getMessage());
                if (devicesCallback != null) {
                    devicesCallback.onError("Ошибка обновления списка устройств: " + e.getMessage());
                }
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    public boolean areServicesRunning() {
        return servicesRunning;
    }

    public void shutdown() {
        stopNetworkServices();
        try {
            if (!updateExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                updateExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            updateExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}