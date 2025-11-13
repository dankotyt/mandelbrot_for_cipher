package com.cipher.client.service.localNetwork;

import com.cipher.core.dto.DeviceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

import static com.cipher.common.utils.NetworkConstants.CONNECTION_PORT;

@Service
@Slf4j
@RequiredArgsConstructor
public class SenderConnectionService {

    private final ConcurrentHashMap<String, Long> rejectedRequests = new ConcurrentHashMap<>();
    private static final long REJECT_COOLDOWN_MS = 60000;

    public boolean sendConnectionRequest(String targetIp, DeviceDTO fromDevice) {
        if (!canSendRequestTo(targetIp)) {
            log.warn("🚫 Запрос к {} заблокирован - недавно был отклонен", targetIp);
            return false;
        }

        try (Socket socket = new Socket()) {
            log.debug("Попытка подключения к {}:{}", targetIp, CONNECTION_PORT);

            socket.connect(new InetSocketAddress(targetIp, CONNECTION_PORT), 2000);

            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            String request = "CONNECT_REQUEST:" + fromDevice.name() + ":" + fromDevice.ip();
            writer.println(request);

            log.info("✅ Запрос на подключение отправлен к: {}", targetIp);
            return true;
        } catch (IOException e) {
            log.warn("❌ Не удалось отправить запрос к {}: {}", targetIp, e.getMessage());
            return false;
        }
    }

    public void sendAcceptResponse(String targetIp, DeviceDTO fromDevice) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetIp, CONNECTION_PORT), 2000);

            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            String response = "ACCEPT_RESPONSE:" + fromDevice.name() + ":" + fromDevice.ip();
            writer.println(response);

            log.info("✅ Подтверждение отправлено к: {}", targetIp);
        } catch (IOException e) {
            log.warn("❌ Не удалось отправить подтверждение к {}: {}", targetIp, e.getMessage());
        }
    }

    public void sendRejectResponse(String targetIp, DeviceDTO fromDevice) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetIp, CONNECTION_PORT), 2000);

            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            String response = "REJECT_RESPONSE:" + fromDevice.name() + ":" + fromDevice.ip();
            writer.println(response);

            log.info("✅ Отклонение отправлено к: {}", targetIp);
        } catch (IOException e) {
            log.warn("❌ Не удалось отправить отклонение к {}: {}", targetIp, e.getMessage());
        }
    }

    private boolean canSendRequestTo(String ip) {
        Long rejectedTime = rejectedRequests.get(ip);
        if (rejectedTime == null) return true;

        boolean canSend = (System.currentTimeMillis() - rejectedTime) > REJECT_COOLDOWN_MS;
        if (canSend) rejectedRequests.remove(ip);
        return canSend;
    }
}