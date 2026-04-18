package com.dankotyt.core.service.network;

import com.dankotyt.core.dto.DeviceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.dankotyt.client.utils.NetworkConstants.APP_PORT;

@Service
@Slf4j
@RequiredArgsConstructor
public class NetworkService {

    private final PeerDiscoveryService peerDiscoveryService;

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

    public List<DeviceDTO> getDiscoveredDevices() {
        try {
            Set<InetAddress> discoveredAddresses =
                    peerDiscoveryService.getDiscoveredPeers();

            List<DeviceDTO> devices = new ArrayList<>();

            for (InetAddress address : discoveredAddresses) {
                try {
                    String deviceName = resolveDeviceName(address);
                    DeviceDTO device = new DeviceDTO(deviceName, address.getHostAddress());
                    devices.add(device);
                } catch (Exception e) {
                    log.warn("Ошибка создания DeviceDTO для {}: {}",
                            address.getHostAddress(), e.getMessage());
                }
            }

            log.debug("Возвращено {} обнаруженных устройств", devices.size());
            return devices;

        } catch (Exception e) {
            log.error("Ошибка получения обнаруженных устройств: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public boolean isAppRunning(String ip) {
        return isPortOpen(ip, APP_PORT, 1000);
    }

    public String getLocalIpAddress() throws SocketException {
        return Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                .flatMap(ni -> Collections.list(ni.getInetAddresses()).stream())
                .filter(addr -> !addr.isLoopbackAddress() && addr.isSiteLocalAddress())
                .map(InetAddress::getHostAddress)
                .findFirst()
                .orElse("127.0.0.1");
    }

    private String resolveDeviceName(InetAddress address) {
        try {
            String hostName = address.getHostName();
            // Если имя хоста совпадает с IP, возвращаем "Устройство [IP]"
            if (hostName.equals(address.getHostAddress())) {
                return "Устройство " + address.getHostAddress();
            }
            return hostName;
        } catch (Exception e) {
            return "Устройство " + address.getHostAddress();
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