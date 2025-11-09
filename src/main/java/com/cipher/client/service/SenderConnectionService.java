package com.cipher.client.service;

import com.cipher.core.dto.DeviceDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

import static com.cipher.common.NetworkConstants.CONNECTION_PORT;

/**Отправляет запрос, его подтверждение и отклонение**/
@Service
@Slf4j
public class SenderConnectionService {

    public boolean sendConnectionRequest(String targetIp, DeviceDTO fromDevice) {
        try (Socket socket = new Socket(targetIp, CONNECTION_PORT)) {
            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            // Формат: TYPE:DATA
            String request = "CONNECT_REQUEST:" + fromDevice.name() + ":" + fromDevice.ip();
            writer.println(request);

            log.info("Запрос на подключение отправлен к: {}", targetIp);
            return true;
        } catch (IOException e) {
            log.warn("Не удалось отправить запрос к {}: {}", targetIp, e.getMessage());
            return false;
        }
    }

    public void sendAcceptResponse(String targetIp, DeviceDTO fromDevice) {
        try (Socket socket = new Socket(targetIp, CONNECTION_PORT)) {
            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            String response = "ACCEPT_RESPONSE:" + fromDevice.name() + ":" + fromDevice.ip();
            writer.println(response);

            log.info("Подтверждение отправлено к: {}", targetIp);
        } catch (IOException e) {
            log.warn("Не удалось отправить подтверждение к {}: {}", targetIp, e.getMessage());
        }
    }

    public void sendRejectResponse(String targetIp, DeviceDTO fromDevice) {
        try (Socket socket = new Socket(targetIp, CONNECTION_PORT)) {
            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            String response = "REJECT_RESPONSE:" + fromDevice.name() + ":" + fromDevice.ip();
            writer.println(response);

            log.info("Отклонение отправлено к: {}", targetIp);
        } catch (IOException e) {
            log.warn("Не удалось отправить отклонение к {}: {}", targetIp, e.getMessage());
        }
    }
}
