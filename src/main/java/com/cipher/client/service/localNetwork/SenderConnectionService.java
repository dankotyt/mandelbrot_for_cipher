package com.cipher.client.service.localNetwork;

import com.cipher.core.dto.DeviceDTO;
import com.cipher.core.service.network.NetworkService;
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
public class SenderConnectionService {

    private final NetworkService networkService;
    private final ConcurrentHashMap<String, Long> rejectedRequests = new ConcurrentHashMap<>();
    private static final long REJECT_COOLDOWN_MS = 60000;
    private final String localIpAddress;

    public SenderConnectionService(NetworkService networkService) {
        this.networkService = networkService;
        try {
            this.localIpAddress = networkService.getLocalIpAddress();
            log.info("🔧 SenderConnectionService initialized with local IP: {}", localIpAddress);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get local IP address", e);
        }
    }

    public boolean sendConnectionRequest(String targetIp, DeviceDTO fromDevice) {
        log.info("🔍 Sending DIRECT connection request to: {} (from: {} {})",
                targetIp, fromDevice.name(), fromDevice.ip());

        // ✅ ТОЧЕЧНОЕ ПОДКЛЮЧЕНИЕ К КОНКРЕТНОМУ IP
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetIp, CONNECTION_PORT), 2000);

            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            String request = "CONNECT_REQUEST:" + fromDevice.name() + ":" + fromDevice.ip();
            writer.println(request);

            log.info("✅ Запрос на подключение отправлен КОНКРЕТНО к: {}", targetIp);
            return true;
        } catch (IOException e) {
            log.warn("❌ Не удалось отправить запрос к {}: {}", targetIp, e.getMessage());
            return false;
        }
    }

    private boolean isOwnIpAddress(String ip) {
        if (ip == null) return true;

        boolean isOwn = ip.equals(localIpAddress) ||
                ip.equals("127.0.0.1") ||
                ip.equals("localhost");

        if (isOwn) {
            log.error("🚫 BLOCKED: Attempt to connect to own IP: {} (local: {})", ip, localIpAddress);
        }

        return isOwn;
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