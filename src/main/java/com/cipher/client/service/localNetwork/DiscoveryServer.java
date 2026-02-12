package com.cipher.client.service.localNetwork;

import com.cipher.client.utils.NetworkConstants;
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
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private Thread broadcasterThread;

    public DiscoveryServer() {
        log.info("Discovery server initialized");
    }

    @Override
    public void run() {
        running.set(true);
        log.info("DiscoveryServer поток запущен");

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            InetAddress broadcastAddr = InetAddress.getByName(
                    NetworkConstants.BROADCAST_ADDRESS);
            byte[] buffer = new byte[1];
            buffer[0] = NetworkConstants.MSG_DISCOVERY;
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
                    broadcastAddr, NetworkConstants.DISCOVERY_PORT);

            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    if (enabled.get()) {
                        socket.send(packet);
                    }
                    Thread.sleep(NetworkConstants.ANNOUNCE_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    if (enabled.get()) {
                        log.error("Ошибка отправки broadcast: {}", e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Ошибка создания сокета: {}", e.getMessage());
        }
    }

    public void start() {
        boolean wasEnabled = enabled.getAndSet(true);

        if (broadcasterThread == null || !broadcasterThread.isAlive()) {
            broadcasterThread = new Thread((Runnable) this, "Discovery-Server");
            broadcasterThread.setDaemon(true);
            broadcasterThread.start();
            log.info("✅ DiscoveryServer поток запущен");
        }

        sendAnnouncement();

        if (!wasEnabled) {
            log.info("✅ DiscoveryServer включен и отправил announcement");
        } else {
            log.info("✅ DiscoveryServer переотправил announcement при повторном входе в сеть");
        }
    }

    /**
     * Принудительная отправка announcement (для кнопки "Обновить")
     */
    public void sendAnnouncement() {
        if (!enabled.get()) {
            log.debug("Устройство невидимо, announcement не отправлен");
            return;
        }

        try (DatagramSocket tempSocket = new DatagramSocket()) {
            tempSocket.setBroadcast(true);
            InetAddress broadcastAddr = InetAddress.getByName(
                    NetworkConstants.BROADCAST_ADDRESS);
            byte[] buffer = new byte[1];
            buffer[0] = NetworkConstants.MSG_DISCOVERY;
            DatagramPacket packet = new DatagramPacket(
                    buffer, buffer.length, broadcastAddr,
                    NetworkConstants.DISCOVERY_PORT);

            // Отправляем 3 пакета для надежности!
            for (int i = 0; i < 3; i++) {
                tempSocket.send(packet);
                Thread.sleep(50);
            }

            log.info("📢 Announcement отправлен (3 пакета)");

        } catch (Exception e) {
            log.warn("Ошибка отправки announcement: {}", e.getMessage());
        }
    }

    /**
     * Отправка запроса на обнаружение (discovery request)
     * Это заставит другие устройства ответить своими announcement
     */
    public void sendGoodbyePacket() {
        new Thread(() -> {
            try {
                Thread.sleep(NetworkConstants.GOODBYE_DELAY_MS);

                try (DatagramSocket goodbyeSocket = new DatagramSocket()) {
                    goodbyeSocket.setBroadcast(true);
                    InetAddress broadcastAddr = InetAddress.getByName(
                            NetworkConstants.BROADCAST_ADDRESS);
                    byte[] buffer = new byte[1];
                    buffer[0] = NetworkConstants.MSG_GOODBYE;
                    DatagramPacket packet = new DatagramPacket(
                            buffer, buffer.length, broadcastAddr,
                            NetworkConstants.DISCOVERY_PORT);

                    // Отправляем 3 goodbye-пакета
                    for (int i = 0; i < 3; i++) {
                        goodbyeSocket.send(packet);
                        log.debug("Goodbye packet #{}/3 sent", i + 1);
                        Thread.sleep(100);
                    }

                    log.info("👋 Goodbye packets sent - device is now invisible");
                }
            } catch (Exception e) {
                log.warn("Error sending goodbye packet: {}", e.getMessage());
            }
        }, "Goodbye-Sender").start();
    }

    public void stop() {
        if (enabled.compareAndSet(true, false)) {
            log.info("DiscoveryServer выключен");
            sendGoodbyePacket();
        }

        running.set(false);

        if (broadcasterThread != null) {
            broadcasterThread.interrupt();
            try {
                broadcasterThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            broadcasterThread = null;
        }
    }
}
