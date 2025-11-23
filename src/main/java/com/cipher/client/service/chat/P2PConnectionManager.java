package com.cipher.client.service.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class P2PConnectionManager {
    private final Map<String, Long> connectionAttempts = new ConcurrentHashMap<>();
    private static final long ATTEMPT_TIMEOUT_MS = 30000; // 30 секунд

    /**
     * Определяет, должно ли устройство инициировать подключение как клиент
     * Логика: основано на времени - кто первый нажал "Чат", тот становится клиентом
     */
    public boolean shouldConnectAsClient(String peerIp) {
        try {
            String myIp = InetAddress.getLocalHost().getHostAddress();

            // Детерминированная логика: устройство с "меньшим" IP становится клиентом
            boolean shouldConnect = myIp.compareTo(peerIp) < 0;

            log.info("🎯 Роль по IP: наш IP={}, пир IP={}, становимся {}",
                    myIp, peerIp, shouldConnect ? "КЛИЕНТОМ" : "СЕРВЕРОМ");

            return shouldConnect;

        } catch (Exception e) {
            log.warn("Ошибка определения роли подключения: {}", e.getMessage());
            // По умолчанию пытаемся подключиться как клиент
            return true;
        }
    }

    /**
     * Сбрасывает историю попыток для конкретного пира
     */
    public void resetAttempts(String peerIp) {
        connectionAttempts.remove(peerIp);
        log.debug("Сброшены попытки подключения к: {}", peerIp);
    }
}