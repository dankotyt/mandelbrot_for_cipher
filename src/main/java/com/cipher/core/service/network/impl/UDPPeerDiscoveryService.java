package com.cipher.core.service.network.impl;

import com.cipher.core.event.DeviceDiscoveredEvent;
import com.cipher.core.event.DeviceLostEvent;
import com.cipher.core.listener.DeviceDiscoveryEventListener;
import com.cipher.core.service.network.PeerDiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UDPPeerDiscoveryService implements PeerDiscoveryService {

    private enum PeerStatus {
        ONLINE,       // В сети (получаем DISCOVERY_MESSAGE)
        OFFLINE       // Вышел (получили GOODBYE_MESSAGE)
    }

    private final ConcurrentHashMap<String, PeerStatus> peerStatus = new ConcurrentHashMap<>();
    private final DeviceDiscoveryEventListener deviceEventListener;

    @Override
    public void onPeerDiscovered(InetAddress peerAddress) {
        if (isOwnAddress(peerAddress)) {
            return;
        }

        String ip = peerAddress.getHostAddress();

        PeerStatus previousStatus = peerStatus.put(ip, PeerStatus.ONLINE);

        if (previousStatus == null) {
            log.info("✅ Устройство обнаружено: {}", ip);
            publishDeviceDiscoveredEvent(peerAddress);
        } else if (previousStatus.equals(PeerStatus.OFFLINE)) {
            log.info("🔄 Устройство вернулось в сеть: {}", ip);
            publishDeviceDiscoveredEvent(peerAddress);

            printOnlinePeers();
        } else {
            log.debug("Устройство все еще в сети: {}", ip);
        }
    }

    @Override
    public void onPeerDisconnected(InetAddress peerAddress) {
        String ip = peerAddress.getHostAddress();
        PeerStatus previousStatus = peerStatus.put(ip, PeerStatus.OFFLINE);

        if (previousStatus == PeerStatus.ONLINE) {
            log.info("👋 Peer gracefully disconnected: {}", ip);
            publishDeviceLostEvent(peerAddress);
            printOnlinePeers();
        }
    }

    @Override
    public Set<InetAddress> getDiscoveredPeers() {
        return peerStatus.entrySet().stream()
                .filter(entry -> entry.getValue() == PeerStatus.ONLINE)
                .map(entry -> {
                    try {
                        return InetAddress.getByName(entry.getKey());
                    } catch (Exception e) {
                        log.warn("Cannot convert IP to InetAddress: {}", entry.getKey());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Override
    public void manuallyAddPeer(String peerAddress) {
        try {
            InetAddress inetAddress = InetAddress.getByName(peerAddress);

            if (isOwnAddress(inetAddress)) {
                log.warn("Вы ввели свой ip-адрес.");
                return;
            }

            if (peerStatus.get(peerAddress) == PeerStatus.ONLINE) {
                log.info("✅ Устройство {} добавлено вручную.", peerAddress);

                publishDeviceDiscoveredEvent(inetAddress);

                printOnlinePeers();
            } else {
                log.info("Устройство не в сети!");
            }
        } catch (UnknownHostException e) {
            log.warn("Введён несуществующий или невалидный ip. " +
                    "Проверьте правильность ввода и попробуйте ещё раз. " +
                    "Ошибка: {}", e.getMessage());
        }
    }

    @Override
    public void clear() {
        peerStatus.clear();
        log.info("✅ UDPPeerDiscoveryService данные очищены");
    }

    private void printOnlinePeers() {
        long onlineCount = peerStatus.values().stream()
                .filter(status -> status == PeerStatus.ONLINE)
                .count();

        if (onlineCount == 0) {
            log.info("📱 Online peers: none");
        } else {
            log.info("📱 Online peers ({}): {}", onlineCount,
                    peerStatus.entrySet().stream()
                            .filter(entry -> entry.getValue() == PeerStatus.ONLINE)
                            .map(Map.Entry::getKey)
                            .collect(Collectors.joining(" ")));
        }
    }

    private boolean isOwnAddress(InetAddress address) {
        try {
            return address.isAnyLocalAddress() ||
                    address.isLoopbackAddress() ||
                    java.net.NetworkInterface.getByInetAddress(address) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void publishDeviceDiscoveredEvent(InetAddress peerAddress) {
        try {
            // Пытаемся получить имя устройства
            String deviceName = resolveDeviceName(peerAddress);

            // Публикуем событие
            DeviceDiscoveredEvent event = new DeviceDiscoveredEvent(this, peerAddress, deviceName);
            deviceEventListener.handleDeviceDiscovered(event);

            log.debug("Событие об обнаружении устройства опубликовано: {} ({})",
                    deviceName, peerAddress.getHostAddress());

        } catch (Exception e) {
            log.warn("Ошибка публикации события: {}", e.getMessage());
            // Все равно публикуем с базовой информацией
            DeviceDiscoveredEvent event = new DeviceDiscoveredEvent(
                    this, peerAddress, peerAddress.getHostAddress());
            deviceEventListener.handleDeviceDiscovered(event);
        }
    }

    private void publishDeviceLostEvent(InetAddress peerAddress) {
        DeviceLostEvent event = new DeviceLostEvent(this, peerAddress);
        deviceEventListener.handleDeviceLost(event);
        log.debug("Событие о потере устройства опубликовано: {}",
                peerAddress.getHostAddress());
    }

    private String resolveDeviceName(InetAddress address) {
        try {
            // Пробуем получить имя хоста
            String hostName = address.getHostName();

            // Если это IP, а не имя, возвращаем IP
            if (hostName.equals(address.getHostAddress())) {
                return "Устройство " + address.getHostAddress();
            }

            // Очищаем имя от домена (если есть)
            if (hostName.contains(".")) {
                return hostName.substring(0, hostName.indexOf('.'));
            }

            return hostName;

        } catch (Exception e) {
            return "Устройство " + address.getHostAddress();
        }
    }
}
