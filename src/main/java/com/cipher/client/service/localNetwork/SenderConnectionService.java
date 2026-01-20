package com.cipher.client.service.localNetwork;

import com.cipher.core.dto.DeviceDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import static com.cipher.common.utils.NetworkConstants.CONNECTION_PORT;

/**Отправляет запрос, его подтверждение и отклонение**/
@Service
@Slf4j
public class SenderConnectionService {

    public boolean sendConnectionRequest(String targetIp, DeviceDTO fromDevice) {
        try (Socket socket = new Socket()) {
            // Устанавливаем таймаут подключения
            socket.connect(new InetSocketAddress(targetIp, CONNECTION_PORT), 5000); // 5 секунд
            socket.setSoTimeout(5000); // Таймаут чтения/записи

            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            String request = "CONNECT_REQUEST:" + fromDevice.name() + ":" + fromDevice.ip();
            writer.println(request);

            log.info("Запрос на подключение отправлен к: {}", targetIp);
            return true;

        } catch (SocketTimeoutException e) {
            log.warn("Таймаут подключения к {}: {}", targetIp, e.getMessage());
            return false;
        } catch (ConnectException e) {
            log.warn("Не удалось подключиться к {}: {}", targetIp, e.getMessage());
            return false;
        } catch (IOException e) {
            log.warn("Ошибка при отправке запроса к {}: {}", targetIp, e.getMessage());
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
