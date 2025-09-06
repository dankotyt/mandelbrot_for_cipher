package com.cipher.client.utils;

import com.cipher.common.exception.NetworkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;

/**
 * Утилитарный класс для проверки сетевого подключения.
 * Предоставляет методы для проверки доступности сети и интернет-соединения.
 */
public class NetworkUtils {
    private static final Logger logger = LoggerFactory.getLogger(NetworkUtils.class);

    /**
     * Проверяет доступность сетевого подключения.
     * Выполняет ping к нескольким DNS серверам для надежности.
     *
     * @return true если сетевое подключение доступно, false в противном случае
     */
    public static boolean isNetworkAvailable() {
        try {
            // Проверяем несколько DNS серверов для надежности
            String[] dnsServers = {"8.8.8.8", "1.1.1.1", "208.67.222.222"};

            for (String dns : dnsServers) {
                try {
                    InetAddress address = InetAddress.getByName(dns);
                    if (address.isReachable(500)) {
                        return true;
                    }
                } catch (Exception e) {
                    logger.debug("DNS сервер {} недоступен: {}", dns, e.getMessage());
                }
            }
            return false;
        } catch (Exception e) {
            logger.warn("Ошибка проверки сети", e);
            return false;
        }
    }

    /**
     * Проверяет сетевое подключение и выбрасывает исключение если оно недоступно.
     *
     * @throws NetworkException если сетевое подключение недоступно
     */
    public static void checkNetworkConnection() {
        if (!isNetworkAvailable()) {
            throw new NetworkException(
                    "Нет подключения к интернету\n\n" +
                            "Пожалуйста, проверьте:\n" +
                            "• Подключение к сети Wi-Fi или Ethernet\n" +
                            "• Настройки firewall\n" +
                            "• Доступность сервера авторизации"
            );
        }
    }
}