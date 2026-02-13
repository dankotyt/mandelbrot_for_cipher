package com.cipher.client.service.localNetwork;

import com.cipher.client.utils.NetworkConstants;
import com.cipher.core.event.DeviceLostEvent;
import com.cipher.core.listener.DeviceDiscoveryEventListener;
import com.cipher.core.service.network.PeerDiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscoveryClient implements Runnable {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Set<InetAddress> discoveredPeers = ConcurrentHashMap.newKeySet();
    private Thread listenerThread;

    private final PeerDiscoveryService discoveryService;
    private final DeviceDiscoveryEventListener deviceEventListener;

    @Override
    public void run() {
        running.set(true);
        log.info("Discovery client started on port {}", NetworkConstants.DISCOVERY_PORT);

        try (DatagramSocket socket = new DatagramSocket(NetworkConstants.DISCOVERY_PORT)) {
            socket.setSoTimeout(1000);
            byte[] buffer = new byte[1024];

            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    byte[] data = packet.getData();
                    if (data.length < 1) continue;

                    byte messageType = data[0];
                    InetAddress senderAddress = packet.getAddress();

                    if (isOwnAddress(senderAddress)) {
                        continue;
                    }

                    if (messageType == NetworkConstants.MSG_DISCOVERY) {
                        if (discoveredPeers.add(senderAddress)) {
                            log.info("Discovered new peer: {}", senderAddress.getHostAddress());
                            discoveryService.onPeerDiscovered(senderAddress);
                            respondWithAnnouncement(senderAddress);
                        }
                    }
                    else if (messageType == NetworkConstants.MSG_GOODBYE) {
                        handleGoodbyeMessage(senderAddress);
                    }

                } catch (SocketTimeoutException e) {
                    // continue
                } catch (IOException e) {
                    if (running.get()) {
                        log.error("Error receiving discovery packet: {}", e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to start discovery client: {}", e.getMessage());
        }

        log.info("Discovery client stopped");
    }

    /**
     * ОТПРАВЛЯЕМ ANNOUNCEMENT ОБРАТНО ОТПРАВИТЕЛЮ
     */
    private void respondWithAnnouncement(InetAddress targetAddress) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(false);

            byte[] buffer = new byte[1];
            buffer[0] = NetworkConstants.MSG_DISCOVERY;

            DatagramPacket packet = new DatagramPacket(
                    buffer, buffer.length,
                    targetAddress,
                    NetworkConstants.DISCOVERY_PORT
            );

            socket.send(packet);
            log.debug("📢 Ответный announcement отправлен для {}", targetAddress.getHostAddress());

        } catch (IOException e) {
            log.warn("Ошибка отправки ответного announcement: {}", e.getMessage());
        }
    }

    /**
     * Отправляет запрос на обнаружение устройств в сети
     */
    public void sendDiscoveryRequest() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                socket.setSoTimeout(2000);

                InetAddress broadcastAddr = InetAddress.getByName(
                        NetworkConstants.BROADCAST_ADDRESS);

                byte[] buffer = new byte[1];
                buffer[0] = NetworkConstants.MSG_DISCOVERY;

                DatagramPacket packet = new DatagramPacket(
                        buffer, buffer.length,
                        broadcastAddr,
                        NetworkConstants.DISCOVERY_PORT
                );

                // Отправляем 3 пакета для надежности
                for (int i = 0; i < 3; i++) {
                    socket.send(packet);
                    Thread.sleep(100);
                }

                log.info("📢 Discovery request отправлен (3 пакета)");

            } catch (Exception e) {
                log.warn("Ошибка отправки discovery request: {}", e.getMessage());
            }
        }, "Discovery-Request").start();
    }

    private void handleGoodbyeMessage(InetAddress senderAddress) {
        if (discoveredPeers.remove(senderAddress)) {
            log.info("👋 Пир покинул сеть: {}", senderAddress.getHostAddress());

            discoveryService.onPeerDisconnected(senderAddress);

            DeviceLostEvent event = new DeviceLostEvent(this, senderAddress);
            deviceEventListener.handleDeviceLost(event);
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

    public void start() {
        if (listenerThread == null || !listenerThread.isAlive()) {
            listenerThread = new Thread(this, "Discovery-Client");
            listenerThread.setDaemon(true);
            listenerThread.start();
        }
    }

    public void stop() {
        running.set(false);
        if (listenerThread != null) {
            listenerThread.interrupt();
            try {
                listenerThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        discoveredPeers.clear();
    }
}
