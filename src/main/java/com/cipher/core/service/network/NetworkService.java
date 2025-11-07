package com.cipher.core.service.network;

import com.cipher.core.dto.DeviceDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
            log.info("Сканирование сети: {}1-254", localNetworkPrefix);

            // Сканируем диапазон в нескольких потоках
            for (int i = 1; i <= 254; i++) {
                String ip = localNetworkPrefix + i;
                CompletableFuture<DeviceDTO> future = checkDeviceAsync(ip);
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            for (CompletableFuture<DeviceDTO> future : futures) {
                DeviceDTO device = future.getNow(null);
                if (device != null) {
                    devices.add(device);
                }
            }

            log.info("Найдено устройств в локальной сети: {}", devices.size());

        } catch (Exception e) {
            log.error("Ошибка при сканировании сети: {}", e.getMessage());
        }

        return devices;
    }

    private CompletableFuture<DeviceDTO> checkDeviceAsync(String ip) {
        return CompletableFuture.supplyAsync(() -> {
            if (isDeviceReachable(ip)) {
                String hostname = getHostname(ip);
                return new DeviceDTO(hostname, ip);
            }
            return null;
        });
    }

    private boolean isDeviceReachable(String ip) {
        return isReachableByPing(ip) || isReachableByPort(ip) || isReachableBySocket(ip);
    }

    private boolean isReachableByPing(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isReachable(500);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isReachableByPort(String ip) {
        int[] ports = {80, 443, 22, 135, 139, 445, 8080};

        for (int port : ports) {
            if (isPortOpen(ip, port, 200)) {
                log.debug("Устройство {} доступно через порт {}", ip, port);
                return true;
            }
        }
        return false;
    }

    private boolean isReachableBySocket(String ip) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, 7), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isPortOpen(String ip, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeout);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String getLocalIpAddress() throws SocketException {
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

    private boolean isReachable(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isReachable(1000); // timeout 1 second
        } catch (Exception e) {
            return false;
        }
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