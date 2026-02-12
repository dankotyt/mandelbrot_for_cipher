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

                    byte messageType = packet.getData()[0];
                    InetAddress senderAddress = packet.getAddress();

                    if (isOwnAddress(senderAddress)) {
                        continue;
                    }

                    if (messageType == NetworkConstants.MSG_DISCOVERY) {
                        if (discoveredPeers.add(senderAddress)) {
                            log.info("Discovered new peer: {}", senderAddress.getHostAddress());
                            discoveryService.onPeerDiscovered(senderAddress);
                        }
                    }
                    else if (messageType == NetworkConstants.MSG_GOODBYE) {
                        discoveryService.onPeerDisconnected(senderAddress);
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

    private void handleGoodbyeMessage(InetAddress senderAddress) {
        if (discoveredPeers.remove(senderAddress)) {
            log.info("Peer left the network: {}", senderAddress.getHostAddress());

            // Уведомляем DeviceDiscoveryEventListener
            if (deviceEventListener != null) {
                // Создаем событие DeviceLostEvent через Spring
                DeviceLostEvent event = new DeviceLostEvent(this, senderAddress);
                // Вызываем метод обработки события
                deviceEventListener.handleDeviceLost(event);
            }

            // Также уведомляем NetworkDiscoveryService
            discoveryService.onPeerDisconnected(senderAddress);
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
