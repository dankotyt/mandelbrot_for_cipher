package com.cipher.core.listener;

import com.cipher.core.dto.DeviceDTO;
import com.cipher.core.event.DeviceDiscoveredEvent;
import com.cipher.core.event.DeviceLostEvent;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

@Slf4j
@Component
public class DeviceDiscoveryEventListener {

    // Храним подписчиков на события
    private final ConcurrentMap<String, Consumer<DeviceDTO>> discoveryConsumers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Consumer<InetAddress>> lostConsumers = new ConcurrentHashMap<>();

    /**
     * Подписаться на события обнаружения устройств
     */
    public String subscribeToDiscovery(Consumer<DeviceDTO> consumer) {
        String id = "discovery-" + System.currentTimeMillis() + "-" + System.nanoTime();
        discoveryConsumers.put(id, consumer);
        return id;
    }

    /**
     * Подписаться на события потери устройств
     */
    public String subscribeToLost(Consumer<InetAddress> consumer) {
        String id = "lost-" + System.currentTimeMillis() + "-" + System.nanoTime();
        lostConsumers.put(id, consumer);
        return id;
    }

    /**
     * Отписаться от событий
     */
    public void unsubscribe(String subscriptionId) {
        discoveryConsumers.remove(subscriptionId);
        lostConsumers.remove(subscriptionId);
    }

    /**
     * Обработка события обнаружения устройства
     */
    @EventListener
    public void handleDeviceDiscovered(DeviceDiscoveredEvent event) {
        log.debug("Получено событие обнаружения устройства: {} ({})",
                event.getDeviceName(), event.getDeviceAddress().getHostAddress());

        // Создаем DTO устройства
        DeviceDTO device = new DeviceDTO(
                event.getDeviceName(),
                event.getDeviceAddress().getHostAddress()
        );

        // Уведомляем всех подписчиков В ГЛАВНОМ ПОТОКЕ JavaFX
        Platform.runLater(() -> {
            discoveryConsumers.values().forEach(consumer -> {
                try {
                    consumer.accept(device);
                } catch (Exception e) {
                    log.error("Ошибка в обработчике обнаружения устройства: {}", e.getMessage());
                }
            });
        });
    }

    /**
     * Обработка события потери устройства
     */
    @EventListener
    public void handleDeviceLost(DeviceLostEvent event) {
        log.debug("Получено событие потери устройства: {}",
                event.getDeviceAddress().getHostAddress());

        // Уведомляем всех подписчиков В ГЛАВНОМ ПОТОКЕ JavaFX
        Platform.runLater(() -> {
            lostConsumers.values().forEach(consumer -> {
                try {
                    consumer.accept(event.getDeviceAddress());
                } catch (Exception e) {
                    log.error("Ошибка в обработчике потери устройства: {}", e.getMessage());
                }
            });
        });
    }
}