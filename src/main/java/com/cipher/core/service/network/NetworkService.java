package com.cipher.core.service.network;

import com.cipher.core.dto.DeviceDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

        try {
            String localNetworkPrefix = getLocalNetworkPrefix();

            // Сканируем диапазон локальной сети (например, 192.168.0.1 - 192.168.0.254)
            for (int i = 1; i <= 254; i++) {
                String ip = localNetworkPrefix + i;
                if (isReachable(ip)) {
                    String hostname = getHostname(ip);
                    devices.add(new DeviceDTO(hostname, ip));
                }
            }

            log.info("Найдено устройств в локальной сети: {}", devices.size());

        } catch (Exception e) {
            log.error("Ошибка при сканировании сети: {}", e.getMessage());
        }

        return devices;
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
            return InetAddress.getByName(ip).getHostName();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}