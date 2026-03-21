package com.cipher.core.service.network;

import com.cipher.client.service.localNetwork.DiscoveryClient;
import com.cipher.client.service.localNetwork.DiscoveryServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkVisibilityService {

    private final DiscoveryServer discoveryServer;
    private final DiscoveryClient discoveryClient;
    private final PeerDiscoveryService peerDiscoveryService;

    private final AtomicBoolean isVisible = new AtomicBoolean(false);
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);

    @PostConstruct
    public void init() {
        try {
            isInitialized.set(true);
            log.info("NetworkVisibilityService инициализирован (невидим)");
        } catch (Exception e) {
            log.error("Ошибка инициализации NetworkVisibilityService: {}", e.getMessage());
        }
    }

    /**
     * Стать видимым в сети
     */
    public void becomeVisible() {
        if (isVisible.compareAndSet(false, true)) {
            try {
                if (!isInitialized.get()) {
                    log.warn("Сервис не инициализирован, выполняется init...");
                    init();
                }

                discoveryClient.start();
                // Запускаем сервер для анонсирования
                discoveryServer.start();

                discoveryClient.sendDiscoveryRequest();

                log.info("✅ Устройство стало видимым в сети");
            } catch (Exception e) {
                log.error("Ошибка при становлении видимым: {}", e.getMessage(), e);
                isVisible.set(false);
                throw new RuntimeException("Не удалось стать видимым", e);
            }
        } else {
            discoveryServer.sendAnnouncement();
            log.debug("Устройство уже видимо");
        }
    }

    /**
     * Стать невидимым в сети
     */
    public void becomeInvisible() {
        if (isVisible.compareAndSet(true, false)) {
            try {
                discoveryClient.stop();
                discoveryServer.stop();
                peerDiscoveryService.clear();

                log.info("🔇 Устройство стало невидимым в сети");
            } catch (Exception e) {
                log.error("Ошибка при становлении невидимым: {}", e.getMessage(), e);
            }
        } else {
            log.debug("Устройство уже невидимо");
        }
    }

    @PreDestroy
    public void shutdown() {
        becomeInvisible();
        log.info("NetworkVisibilityService остановлен");
    }
}
