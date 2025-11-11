package com.cipher.client.service.localNetwork;

import com.cipher.common.utils.NetworkConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class DiscoveryServer implements Runnable {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread broadcasterThread;

    public DiscoveryServer() {
        log.info("Discovery server initialized");
    }

    @Override
    public void run() {
        running.set(true);
        log.info("Discovery server started, broadcasting to {}", NetworkConstants.BROADCAST_ADDRESS);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            InetAddress broadcastAddr = InetAddress.getByName(NetworkConstants.BROADCAST_ADDRESS);
            byte[] buffer = NetworkConstants.DISCOVERY_MESSAGE.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
                    broadcastAddr, NetworkConstants.DISCOVERY_PORT);

            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    socket.send(packet);
                    log.debug("Broadcast discovery message sent");

                    Thread.sleep(NetworkConstants.ANNOUNCE_INTERVAL_MS);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    log.error("Error sending broadcast message: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Failed to start discovery server: {}", e.getMessage());
        }

        log.info("Discovery server stopped");
    }

    public void start() {
        if (broadcasterThread == null || !broadcasterThread.isAlive()) {
            broadcasterThread = new Thread(this, "Discovery-Server");
            broadcasterThread.setDaemon(true);
            broadcasterThread.start();
        }
    }

    public void stop() {
        running.set(false);
        if (broadcasterThread != null) {
            broadcasterThread.interrupt();
            try {
                broadcasterThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
