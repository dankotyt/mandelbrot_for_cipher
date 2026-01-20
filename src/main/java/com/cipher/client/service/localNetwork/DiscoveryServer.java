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
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private Thread broadcasterThread;
    private DatagramSocket socket;

    public DiscoveryServer() {
        log.info("Discovery server initialized");
    }

    @Override
    public void run() {
        running.set(true);
        log.info("DiscoveryServer поток запущен (enabled={})", enabled.get());

        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            InetAddress broadcastAddr = InetAddress.getByName(NetworkConstants.BROADCAST_ADDRESS);
            byte[] buffer = NetworkConstants.DISCOVERY_MESSAGE.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
                    broadcastAddr, NetworkConstants.DISCOVERY_PORT);

            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    // Отправляем только если сервер включен
                    if (enabled.get()) {
                        socket.send(packet);
                        log.trace("Broadcast сообщение отправлено");
                    } else {
                        // Если выключен, просто спим
                        log.trace("DiscoveryServer выключен, ожидание...");
                    }

                    Thread.sleep(NetworkConstants.ANNOUNCE_INTERVAL_MS);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    if (enabled.get()) {
                        log.error("Ошибка отправки broadcast сообщения: {}", e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Ошибка создания сокета: {}", e.getMessage());
        } finally {
            closeSocket();
            log.info("DiscoveryServer поток остановлен");
        }
    }

    private void closeSocket() {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
                log.debug("Сокет DiscoveryServer закрыт");
            } catch (Exception e) {
                log.warn("Ошибка закрытия сокета: {}", e.getMessage());
            }
        }
    }

    public void start() {
        if (broadcasterThread == null || !broadcasterThread.isAlive()) {
            setEnabled(true);
            broadcasterThread = new Thread(this, "Discovery-Server");
            broadcasterThread.setDaemon(true);
            broadcasterThread.start();
            log.info("DiscoveryServer запущен и включен");
        } else {
            // Если поток уже работает, просто включаем
            setEnabled(true);
            log.info("DiscoveryServer уже запущен, включена отправка сообщений");
        }
    }

    public void stop() {
        setEnabled(false); // Выключаем отправку сообщений
        running.set(false); // Останавливаем цикл

        if (broadcasterThread != null) {
            broadcasterThread.interrupt();
            try {
                broadcasterThread.join(2000); // Даем время на завершение
                if (broadcasterThread.isAlive()) {
                    log.warn("DiscoveryServer поток не завершился вовремя");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            broadcasterThread = null;
        }

        closeSocket(); // Закрываем сокет
        log.info("DiscoveryServer полностью остановлен");
    }

    public void setEnabled(boolean enabled) {
        boolean wasEnabled = this.enabled.getAndSet(enabled);
        if (wasEnabled != enabled) {
            log.info("DiscoveryServer {}",
                    enabled ? "включен (устройство видимо)" : "выключен (устройство невидимо)");
        }
    }

    public boolean isEnabled() {
        return enabled.get();
    }
}
