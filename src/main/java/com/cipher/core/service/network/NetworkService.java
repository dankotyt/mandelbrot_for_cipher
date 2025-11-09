package com.cipher.core.service.network;

import com.cipher.core.dto.DeviceDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static com.cipher.common.NetworkConstants.APP_PORT;

@Service
@Slf4j
public class NetworkService {

    public DeviceDTO getCurrentDevice() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            String ip = getLocalIpAddress();

            return new DeviceDTO(hostname, ip);
        } catch (Exception e) {
            log.error("Ошибка получения информации об устройстве: {}", e.getMessage());
            return new DeviceDTO("UNKNOWN", "127.0.0.1");
        }
    }

    public List<DeviceDTO> discoverLocalDevices() {
        List<DeviceDTO> devices = new ArrayList<>();
        List<CompletableFuture<DeviceDTO>> futures = new ArrayList<>();

        try {
            String localNetworkPrefix = getLocalNetworkPrefix();
            log.info("Сканирование сети на порту {}: {}1-254", APP_PORT, localNetworkPrefix);

            ExecutorService executor = Executors.newFixedThreadPool(50);

            for (int i = 1; i <= 254; i++) {
                String ip = localNetworkPrefix + i;
                CompletableFuture<DeviceDTO> future = CompletableFuture.supplyAsync(() -> {
                    if (isAppRunning(ip)) {
                        String hostname = getHostname(ip);
                        log.info("✅ Найдено устройство с программой: {} ({})", ip, hostname);
                        return new DeviceDTO(hostname, ip);
                    }
                    return null;
                }, executor);
                futures.add(future);
            }

            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0])
            );

            try {
                allFutures.get(30, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("Сканирование завершено по таймауту, но некоторые устройства могут быть найдены");
            }

            for (CompletableFuture<DeviceDTO> future : futures) {
                try {
                    DeviceDTO device = future.getNow(null);
                    if (device != null) {
                        devices.add(device);
                    }
                } catch (Exception ignored) {
                }
            }

            executor.shutdown();
            log.info("Найдено устройств с программой: {}", devices.size());

        } catch (Exception e) {
            log.error("Ошибка при сканировании сети: {}", e.getMessage());
        }

        return devices;
    }

    public boolean isAppRunning(String ip) {
        return isPortOpen(ip, APP_PORT, 1000);
    }

    private boolean isPortOpen(String ip, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeout);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getLocalIpAddress() throws SocketException {
        return Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                .flatMap(ni -> Collections.list(ni.getInetAddresses()).stream())
                .filter(addr -> !addr.isLoopbackAddress() && addr.isSiteLocalAddress())
                .map(InetAddress::getHostAddress)
                .findFirst()
                .orElse("127.0.0.1");
    }

    private String getLocalNetworkPrefix() throws SocketException {
        String localIp = getLocalIpAddress();
        return localIp.substring(0, localIp.lastIndexOf('.') + 1);
    }

    private String getHostname(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            String hostname = address.getHostName();
            return hostname.equals(ip) ? "UNKNOWN" : hostname;
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}