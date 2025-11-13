package com.cipher.client.service.localNetwork;

import com.cipher.common.utils.NetworkConstants;
import com.cipher.core.service.network.NetworkDiscoveryService;
import com.cipher.core.service.network.NetworkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class DiscoveryClient implements Runnable {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Set<InetAddress> discoveredPeers = ConcurrentHashMap.newKeySet();
    private final NetworkDiscoveryService discoveryService;
    private Thread listenerThread;
    private final String localIpAddress;

    public DiscoveryClient(NetworkDiscoveryService discoveryService, NetworkService networkService) {
        this.discoveryService = discoveryService;
        try {
            this.localIpAddress = networkService.getLocalIpAddress();
            log.info("🔧 DiscoveryClient initialized with local IP: {}", localIpAddress);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get local IP address", e);
        }
    }

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

                    String message = new String(packet.getData(), 0, packet.getLength(),
                            StandardCharsets.UTF_8).trim();

                    if (NetworkConstants.DISCOVERY_MESSAGE.equals(message)) {
                        InetAddress senderAddress = packet.getAddress();
                        String senderIp = senderAddress.getHostAddress();

                        // ✅ СТРОГАЯ ПРОВЕРКА - игнорируем собственные сообщения
                        if (isOwnMessage(senderIp) || isOwnAddress(senderAddress)) {
                            log.debug("🚫 Ignoring own discovery message from: {}", senderIp);
                            continue;
                        }

                        if (discoveredPeers.add(senderAddress)) {
                            log.info("🆕 Discovered new peer: {}", senderIp);
                            discoveryService.onPeerDiscovered(senderAddress);
                        }
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

    private boolean isOwnMessage(String senderIp) {
        if (senderIp == null) return true;

        boolean isOwn = senderIp.equals(localIpAddress) ||
                senderIp.equals("127.0.0.1") ||
                senderIp.equals("localhost");

        if (isOwn) {
            log.debug("🚫 Filtered own IP in discovery: {}", senderIp);
        }

        return isOwn;
    }

    private boolean isOwnAddress(InetAddress address) {
        try {
            return address.isAnyLocalAddress() ||
                    address.isLoopbackAddress() ||
                    NetworkInterface.getByInetAddress(address) != null;
        } catch (Exception e) {
            log.warn("Error checking network interface for address {}: {}",
                    address.getHostAddress(), e.getMessage());
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

    public Set<InetAddress> getDiscoveredPeers() {
        return Set.copyOf(discoveredPeers);
    }

}
