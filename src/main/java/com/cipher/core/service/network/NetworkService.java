package com.cipher.core.service.network;

import com.cipher.core.dto.DeviceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static com.cipher.common.utils.NetworkConstants.APP_PORT;

@Service
@Slf4j
@RequiredArgsConstructor
public class NetworkService {

    private final NetworkDiscoveryService networkDiscoveryService;

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

        try {
            // 1. Активное TCP сканирование
            String localNetworkPrefix = getLocalNetworkPrefix();
            log.info("🔍 Сканирование сети на порту {}: {}1-254", APP_PORT, localNetworkPrefix);

            List<CompletableFuture<DeviceDTO>> tcpFutures = performActiveTcpScan(localNetworkPrefix);

            // 2. Добавляем устройства из discovery service (UDP)
            Set<InetAddress> discoveredAddresses = networkDiscoveryService.getDiscoveredPeers();
            int udpDevicesCount = 0;
            for (InetAddress address : discoveredAddresses) {
                String ip = address.getHostAddress();
                if (!isSelfIpAddress(ip)) {
                    String hostname = getHostname(ip);
                    devices.add(new DeviceDTO(hostname, ip));
                    udpDevicesCount++;
                    log.info("📡 Добавлено устройство из UDP discovery: {} ({})", ip, hostname);
                }
            }

            // 3. Ждем завершения TCP сканирования
            CompletableFuture<Void> allTcpFutures = CompletableFuture.allOf(
                    tcpFutures.toArray(new CompletableFuture[0])
            );

            try {
                allTcpFutures.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("⏰ TCP сканирование завершено по таймауту");
            }

            // 4. Добавляем результаты TCP сканирования
            int tcpDevicesCount = 0;
            for (CompletableFuture<DeviceDTO> future : tcpFutures) {
                try {
                    DeviceDTO device = future.getNow(null);
                    if (device != null && !containsDevice(devices, device)) {
                        devices.add(device);
                        tcpDevicesCount++;
                    }
                } catch (Exception ignored) {}
            }

            log.info("🎯 Итого найдено устройств: {} (TCP: {}, UDP: {})",
                    devices.size(), tcpDevicesCount, udpDevicesCount);

        } catch (Exception e) {
            log.error("❌ Ошибка при сканировании сети: {}", e.getMessage());
        }

        return devices;
    }

    private List<CompletableFuture<DeviceDTO>> performActiveTcpScan(String networkPrefix) {
        List<CompletableFuture<DeviceDTO>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(20); // Уменьшаем пул для стабильности

        for (int i = 1; i <= 254; i++) {
            String ip = networkPrefix + i;
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

        // Завершаем executor после выполнения всех задач
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(executor::shutdown);

        return futures;
    }

    private boolean containsDevice(List<DeviceDTO> devices, DeviceDTO newDevice) {
        return devices.stream().anyMatch(device -> device.ip().equals(newDevice.ip()));
    }

    private boolean isSelfIpAddress(String ip) {
        try {
            String localIp = getLocalIpAddress();
            return ip.equals(localIp) || ip.equals("127.0.0.1") || ip.equals("localhost");
        } catch (Exception e) {
            return false;
        }
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
        log.debug("🔧 Getting local IP address...");

        List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());

        for (NetworkInterface ni : interfaces) {
            // Пропускаем выключенные интерфейсы и loopback
            if (!ni.isUp() || ni.isLoopback()) {
                continue;
            }

            for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                // Берем только IPv4 адреса
                if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                    String ip = addr.getHostAddress();
                    log.info("🔧 Selected local IP: {} from interface: {}", ip, ni.getDisplayName());
                    return ip;
                }
            }
        }

        // Fallback
        try {
            String fallbackIp = InetAddress.getLocalHost().getHostAddress();
            log.warn("⚠️ Using fallback local IP: {}", fallbackIp);
            return fallbackIp;
        } catch (Exception e) {
            log.error("🚨 Failed to get any local IP, using 127.0.0.1");
            return "127.0.0.1";
        }
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