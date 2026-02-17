package com.cipher.core.service.network.impl;

import com.cipher.client.service.chat.ChatService;
import com.cipher.client.service.localNetwork.SenderConnectionService;
import com.cipher.client.utils.NetworkConstants;
import com.cipher.core.dto.connection.ConnectionRequestDTO;
import com.cipher.core.dto.DeviceDTO;
import com.cipher.core.model.ECDHKeyPair;
import com.cipher.core.model.SignedConnectionPacket;
import com.cipher.core.service.network.ConnectionService;
import com.cipher.core.service.network.DigitalSignatureService;
import com.cipher.core.service.network.CryptoKeyManager;
import com.cipher.core.service.network.NetworkService;
import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import javafx.application.Platform;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для управления подключениями между устройствами
 * Обрабатывает входящие запросы подключения и управляет состоянием соединений
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ConnectionServiceImpl implements ConnectionService {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionServiceImpl.class);

    private final DialogDisplayer dialogDisplayer;
    private final NetworkService networkService;
    private final SenderConnectionService senderConnectionService;
    private final CryptoKeyManager cryptoKeyManager;
    private final DigitalSignatureService signatureService;
    private final SceneManager sceneManager;
    private final ChatService chatService;

    private ServerSocket appServerSocket;
    private ServerSocket cryptoServerSocket;

    private boolean serverRunning = false;

    private final Map<String, ConnectionRequestDTO> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, ConnectionRequestDTO> establishedConnections = new ConcurrentHashMap<>();
    private final List<ConnectionListener> listeners = new ArrayList<>();
    private final Map<String, Object> requestLocks = new ConcurrentHashMap<>();
    private final Map<String, Long> blockedPeers = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        startAppServer();
        startCryptoServer();
    }

    @PreDestroy
    public void cleanup() {
        serverRunning = false;
        closeServerSocket(appServerSocket, "App");
        closeServerSocket(cryptoServerSocket, "Crypto");
    }

    private void closeServerSocket(ServerSocket socket, String name) {
        if (socket != null) {
            try {
                socket.close();
                logger.info("Сервер {} остановлен", name);
            } catch (IOException e) {
                logger.warn("Ошибка закрытия сервера {}: {}", name, e.getMessage());
            }
        }
    }

    // ==================== Серверная часть ====================

    /**
     * Запускает сервер приложения для обработки входящих подключений
     */
    private void startAppServer() {
        new Thread(() -> {
            try {
                appServerSocket = new ServerSocket(NetworkConstants.APP_PORT);
                serverRunning = true;
                logger.info("Сервер приложения запущен на порту {}", NetworkConstants.APP_PORT);

                while (serverRunning) {
                    Socket clientSocket = appServerSocket.accept();
                    processIncomingConnection(clientSocket);
                }
            } catch (IOException e) {
                if (serverRunning) {
                    logger.error("Ошибка сервера приложения: {}", e.getMessage());
                }
            }
        }, "App-Server").start();
    }

    /**
     * Сервер для криптографических подписанных пакетов
     */
    private void startCryptoServer() {
        new Thread(() -> {
            try {
                cryptoServerSocket = new ServerSocket(NetworkConstants.SIGNED_PACKET_PORT);
                logger.info("Крипто-сервер запущен на порту {}", NetworkConstants.SIGNED_PACKET_PORT);

                while (serverRunning) {
                    Socket clientSocket = cryptoServerSocket.accept();
                    processSignedPacket(clientSocket);
                }
            } catch (IOException e) {
                if (serverRunning) {
                    logger.error("Ошибка крипто-сервера: {}", e.getMessage());
                }
            }
        }, "Crypto-Server").start();
    }

    /**
     * Обрабатывает входящее соединение
     */
    private void processIncomingConnection(Socket clientSocket) {
        new Thread(() -> {
            String clientIp = clientSocket.getInetAddress().getHostAddress();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                String message = reader.readLine();

                if (message != null) {
                    logger.info("Получено текстовое сообщение от {}: {}", clientIp, message);
                    parseAndHandleMessage(message, clientIp);
                }
            } catch (IOException e) {
                logger.error("Ошибка обработки текстового сообщения от {}: {}", clientIp, e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    // игнорируем
                }
            }
        }).start();
    }

    /**
     * Парсит и обрабатывает входящее сообщение
     */
    private void parseAndHandleMessage(String message, String clientIp) {
        logger.info("ПОЛУЧЕНО СООБЩЕНИЕ от {}: '{}'", clientIp, message);

        String[] parts = message.split(":", 3);
        if (parts.length < 3) {
            logger.warn("❌ Некорректный формат сообщения от {}: {}", clientIp, message);
            return;
        }

        String messageType = parts[0];
        String deviceName = parts[1];
        String deviceIp = parts[2];
        DeviceDTO remoteDevice = new DeviceDTO(deviceName, deviceIp);

        logger.info("Тип сообщения: {}, от устройства: {} ({})",
                messageType, deviceName, deviceIp);

        switch (messageType) {
            case "CONNECT_REQUEST":
                processIncomingRequest(createConnectionRequest(remoteDevice, ConnectionRequestDTO.RequestStatus.PENDING));
                break;
            case "ACCEPT_RESPONSE":
                processIncomingAccept(remoteDevice, clientIp);
                break;
            case "REJECT_RESPONSE":
                processIncomingReject(remoteDevice, clientIp);
                break;
            default:
                logger.warn("❓ Неизвестный тип сообщения: {}", messageType);
        }
    }

    @Override
    public void processIncomingRequest(ConnectionRequestDTO request) {
        if (isBlocked(request.fromDeviceIp())) {
            logger.warn("⛔ Запрос от заблокированного пира {} отклонен", request.fromDeviceIp());
            return;
        }
        // Показываем диалог пользователю
        Platform.runLater(() -> {
            boolean accepted = dialogDisplayer.showConfirmationDialog(
                    "Запрос на подключение",
                    "Входящее подключение",
                    String.format("Получен запрос на подключение от:%nУстройство: %s%nIP: %s%n%nПринять подключение и открыть чат?",
                            request.toDeviceName(), request.toDeviceIp()),
                    "Принять",
                    "Отклонить"
            );

            if (accepted) {
                // Отправляем подтверждение
                DeviceDTO currentDevice = networkService.getCurrentDevice();
                senderConnectionService.sendAcceptResponse(request.toDeviceIp(), currentDevice);

                logger.info("🔐 Ожидание обмена подписями от {}", request.toDeviceIp());
            } else {
                // Отправляем отклонение
                DeviceDTO currentDevice = networkService.getCurrentDevice();
                senderConnectionService.sendRejectResponse(request.toDeviceIp(), currentDevice);

                // Отклоняем соединение
                rejectConnection(request);
            }
        });
    }

    @Override
    public void processIncomingAccept(DeviceDTO remoteDevice, String clientIp) {
        // Создаем запрос со статусом ACCEPTED
        ConnectionRequestDTO request = createConnectionRequest(remoteDevice, ConnectionRequestDTO.RequestStatus.ACCEPTED);

        // Обновляем статус соединения
        acceptConnection(request);

        initiateCryptoExchange(request);
    }

    @Override
    public void processIncomingReject(DeviceDTO remoteDevice, String clientIp) {
        // Создаем запрос со статусом REJECTED
        ConnectionRequestDTO request = createConnectionRequest(remoteDevice, ConnectionRequestDTO.RequestStatus.REJECTED);

        blockPeer(clientIp);

        // Обновляем статус соединения
        rejectConnection(request);

        // Показываем уведомление пользователю
        Platform.runLater(() ->
                dialogDisplayer.showErrorDialog(
                        String.format("Устройство %s отклонило ваш запрос на " +
                                "подключение", remoteDevice.name())));
    }

    // ==================== КРИПТОГРАФИЧЕСКИЙ ОБМЕН (УРОВЕНЬ 2) ====================

    /**
     * Инициатор отправляет подписанный пакет после получения ACCEPT_RESPONSE
     */
    private void initiateCryptoExchange(ConnectionRequestDTO requestDTO) {
        new Thread(() -> {
            try {
                logger.info("🔐 Начинаем криптообмен с {}", requestDTO.toDeviceIp());
                ECDHKeyPair currentKeys = cryptoKeyManager.getCurrentKeys();
                InetAddress currentDeviceIp = InetAddress.getByName(networkService.getCurrentDevice().ip());

                // 1. СОЗДАЕМ пакет
                SignedConnectionPacket packet = SignedConnectionPacket.createRequest(
                        currentDeviceIp,
                        networkService.getCurrentDevice().name(),
                        currentKeys.getPublicKeyBytes(),
                        signatureService.publicKeyToBytes(signatureService.getSignatureKeyPair().getPublic())
                );

                // 2. ПОДПИСЫВАЕМ
                signatureService.signPacket(packet);

                // 3. ОТПРАВЛЯЕМ
                SignedConnectionPacket response = senderConnectionService.sendSignedPacket(
                        requestDTO.toDeviceIp(),
                        packet
                );

                // 4. ОБРАБАТЫВАЕМ ответ
                if (response == null) {
                    Platform.runLater(() ->
                            dialogDisplayer.showErrorDialog("Не удалось получить ответ от " + requestDTO.toDeviceIp())
                    );
                    rejectConnection(requestDTO);
                    return;
                }

                // 5. Проверяем подпись
                if (!signatureService.verifyPacket(response)) {
                    Platform.runLater(() ->
                            dialogDisplayer.showErrorDialog("Ошибка проверки цифровой подписи от " + requestDTO.toDeviceIp())
                    );
                    rejectConnection(requestDTO);
                }
                currentKeys.computeSharedSecret(response.getDhPublicKey());

                // 7. Сохраняем соединение
                cryptoKeyManager.addConnection(response.getSenderAddress(), currentKeys);
                cryptoKeyManager.setConnectedPeer(response.getSenderAddress());

                // 8. Обновляем статус в ConnectionServiceImpl
                acceptConnection(requestDTO);

                boolean chatConnected = chatService.connectToPeer(requestDTO.toDeviceIp());
                if (chatConnected) {
                    logger.info("P2P чат соединение установлено с: {}", requestDTO.toDeviceIp());

                    // Открываем чат
                    Platform.runLater(() -> {
                        sceneManager.showChatPanel(new DeviceDTO(requestDTO.toDeviceName(), requestDTO.toDeviceIp()));
                        logger.info("Чат открыт с: {}", requestDTO.toDeviceIp());
                    });
                } else {
                    logger.error("Не удалось установить P2P соединение с: {}", requestDTO.toDeviceIp());
                    Platform.runLater(() ->
                            dialogDisplayer.showErrorDialog("Не удалось установить P2P соединение для чата")
                    );
                }

            } catch (Exception e) {
                logger.error("Ошибка в initiateCryptoExchange: {}", e.getMessage(), e);
                Platform.runLater(() ->
                        dialogDisplayer.showErrorDialog("Ошибка при установке защищенного соединения: " + e.getMessage())
                );
                rejectConnection(requestDTO);
            }
        }).start();
    }

    // ==================== ОБРАБОТКА ПОДПИСАННЫХ ПАКЕТОВ ====================

    private void processSignedPacket(Socket clientSocket) {
        new Thread(() -> {
            String clientIp = clientSocket.getInetAddress().getHostAddress();

            try (ObjectInputStream ois = new ObjectInputStream(clientSocket.getInputStream());
                 ObjectOutputStream oos = new ObjectOutputStream(clientSocket.getOutputStream())) {

                SignedConnectionPacket packet = (SignedConnectionPacket) ois.readObject();
                logger.info("Получен подписанный пакет от {}", clientIp);

                // Проверяем подпись
                if (!signatureService.verifyPacket(packet)) {
                    logger.error("Неверная подпись от {}", clientIp);
                    sendErrorResponse(oos, "Invalid signature");
                    return;
                }

                // Проверяем временную метку
                if (!packet.isTimestampValid()) {
                    logger.error("Просроченный пакет от {}", clientIp);
                    sendErrorResponse(oos, "Packet expired");
                    return;
                }

                ECDHKeyPair currentKeys = cryptoKeyManager.getCurrentKeys();

                currentKeys.computeSharedSecret(packet.getDhPublicKey());

                InetAddress currentDeviceIp = InetAddress.getByName(networkService.getCurrentDevice().ip());

                // Создаем ответный пакет
                SignedConnectionPacket response = SignedConnectionPacket.createResponse(
                        currentDeviceIp,
                        true,
                        "Crypto exchange successful",
                        currentKeys.getPublicKeyBytes(),
                        signatureService.publicKeyToBytes(signatureService.getSignatureKeyPair().getPublic()),
                        0
                );

                signatureService.signPacket(response);

                // Отправляем ответ
                oos.writeObject(response);
                oos.flush();

                cryptoKeyManager.addConnection(packet.getSenderAddress(), currentKeys);
                cryptoKeyManager.setConnectedPeer(packet.getSenderAddress());
                logger.info("Общий секрет вычислен для {}", clientIp);

                if (response.getSenderAddress().getHostAddress().equals(clientIp)) {
                    DeviceDTO remoteDevice = new DeviceDTO(packet.getSenderName(), clientIp);
                    ConnectionRequestDTO requestDTO = createConnectionRequest(
                            remoteDevice,
                            ConnectionRequestDTO.RequestStatus.ACCEPTED
                    );
                    logger.info("Все ок. IP из response и clientIp совпадают");
                    acceptConnection(requestDTO);
                    boolean chatConnected = chatService.connectToPeer(clientIp);
                    if (chatConnected) {
                        logger.info("✅ P2P чат соединение установлено с: {}", clientIp);

                        // Открываем чат
                        Platform.runLater(() -> {
                            sceneManager.showChatPanel(remoteDevice);
                            logger.info("✅ Чат открыт с: {}", clientIp);
                        });
                    }
                } else {
                    logger.error("IP из response и clientIp не совпадают. response : {}, " +
                            "clientIp: {}", response.getSenderAddress().getHostAddress(), clientIp);
                    logger.error("❌ Не удалось установить P2P соединение с: {}", clientIp);
                    Platform.runLater(() ->
                            dialogDisplayer.showErrorDialog("Не удалось установить P2P соединение для чата")
                    );
                }
            } catch (Exception e) {
                logger.error("Ошибка обработки подписанного пакета от {}: {}", clientIp, e.getMessage());
            }
        }).start();
    }

    /**
     * Отправляет ответ с ошибкой
     */
    private void sendErrorResponse(ObjectOutputStream oos, String errorMessage) throws IOException {
        try {
            InetAddress currentDeviceIp = InetAddress.getByName(networkService.getCurrentDevice().ip());
            SignedConnectionPacket errorResponse = SignedConnectionPacket.createResponse(
                    currentDeviceIp,
                    false,
                    errorMessage,
                    null,
                    signatureService.publicKeyToBytes(signatureService.getSignatureKeyPair().getPublic()),
                    0
            );
            signatureService.signPacket(errorResponse);
            oos.writeObject(errorResponse);
            oos.flush();
        } catch (Exception e) {
            logger.error("Ошибка отправки error response: {}", e.getMessage());
        }
    }

    // ==================== Клиентская часть ====================

    @Override
    public void initiateConnection(DeviceDTO toDevice) {
        String lockKey = toDevice.ip();
        Object lock = requestLocks.computeIfAbsent(lockKey, k -> new Object());

        synchronized (lock) {
            try {
                if (isConnectionPending(toDevice.ip())) {
                    logger.warn("Запрос к {} уже отправлен", toDevice.ip());
                    return;
                }

                // Отправляем запрос через сеть
                DeviceDTO currentDevice = networkService.getCurrentDevice();
                boolean sent = senderConnectionService.sendConnectionRequest(toDevice.ip(), currentDevice);

                if (sent) {
                    // Создаем и сохраняем запрос
                    ConnectionRequestDTO request = createConnectionRequest(toDevice, ConnectionRequestDTO.RequestStatus.PENDING);
                    String requestId = generateRequestId(request);
                    pendingRequests.put(requestId, request);

                    // Уведомляем слушателей
                    notifyRequestReceived(request);
                } else {
                    logger.error("Не удалось отправить запрос к {}", toDevice.ip());
                }

            } catch (Exception e) {
                logger.error("Ошибка при отправке запроса: {}", e.getMessage(), e);
            }
        }
    }

    // ==================== БЛОКИРОВКА ====================

    private void blockPeer(String ip) {
        blockedPeers.put(ip, System.currentTimeMillis() + 30000); // 30 секунд
        logger.info("⛔ Пир {} заблокирован на 30 секунд", ip);
    }

    private boolean isBlocked(String ip) {
        Long blockUntil = blockedPeers.get(ip);
        if (blockUntil == null) return false;
        if (System.currentTimeMillis() > blockUntil) {
            blockedPeers.remove(ip);
            return false;
        }
        return true;
    }

    // ==================== Управление статусом соединений ====================

    @Override
    public void acceptConnection(ConnectionRequestDTO request) {
        try {
            String requestId = generateRequestId(request);

            // Обновляем статус запроса
            ConnectionRequestDTO acceptedRequest = new ConnectionRequestDTO(
                    request.fromDeviceName(),
                    request.fromDeviceIp(),
                    request.toDeviceName(),
                    request.toDeviceIp(),
                    LocalDateTime.now(),
                    ConnectionRequestDTO.RequestStatus.ACCEPTED
            );

            // Перемещаем из pending в established
            pendingRequests.remove(requestId);
            establishedConnections.put(requestId, acceptedRequest);

            logger.info("Подключение принято: {} -> {}",
                    request.fromDeviceIp(), request.toDeviceIp());

            // Уведомляем слушателей
            notifyConnectionAccepted(acceptedRequest);
            notifyConnectionEstablished(acceptedRequest);

        } catch (Exception e) {
            logger.error("Ошибка при принятии соединения: {}", e.getMessage(), e);
        }
    }

    @Override
    public void rejectConnection(ConnectionRequestDTO request) {
        try {
            String requestId = generateRequestId(request);

            // Обновляем статус запроса
            ConnectionRequestDTO rejectedRequest = new ConnectionRequestDTO(
                    request.fromDeviceName(),
                    request.fromDeviceIp(),
                    request.toDeviceName(),
                    request.toDeviceIp(),
                    request.timestamp(),
                    ConnectionRequestDTO.RequestStatus.REJECTED
            );

            // Удаляем из pending
            pendingRequests.remove(requestId);

            logger.info("Подключение отклонено: {} -> {}",
                    request.fromDeviceIp(), request.toDeviceIp());

            // Уведомляем слушателей
            notifyConnectionRejected(rejectedRequest);

        } catch (Exception e) {
            logger.error("Ошибка при отклонении соединения: {}", e.getMessage(), e);
        }
    }

    @Override
    public void disconnect(String deviceIp) {
        // Находим и удаляем все соединения с этим устройством
        List<String> toRemove = new ArrayList<>();

        establishedConnections.forEach((id, request) -> {
            if (request.fromDeviceIp().equals(deviceIp) || request.toDeviceIp().equals(deviceIp)) {
                toRemove.add(id);
            }
        });

        toRemove.forEach(id -> {
            establishedConnections.remove(id);
            logger.info("Соединение разорвано: {}", id);
        });

        // Уведомляем слушателей
        notifyConnectionDisconnected(deviceIp);
    }

    // ==================== Проверки статуса ====================

    @Override
    public boolean isConnectionPending(String deviceIp) {
        return pendingRequests.values().stream()
                .anyMatch(request -> request.toDeviceIp().equals(deviceIp) &&
                        request.status() == ConnectionRequestDTO.RequestStatus.PENDING);
    }

    @Override
    public boolean isConnectionEstablished(String deviceIp) {
        return establishedConnections.values().stream()
                .anyMatch(request -> (request.fromDeviceIp().equals(deviceIp) ||
                        request.toDeviceIp().equals(deviceIp)) &&
                        request.status() == ConnectionRequestDTO.RequestStatus.ACCEPTED);
    }

    @Override
    public ConnectionRequestDTO getConnectionStatus(String deviceIp) {
        // Сначала ищем в установленных соединениях
        Optional<ConnectionRequestDTO> established = establishedConnections.values().stream()
                .filter(request -> (request.fromDeviceIp().equals(deviceIp) ||
                        request.toDeviceIp().equals(deviceIp)) &&
                        request.status() == ConnectionRequestDTO.RequestStatus.ACCEPTED)
                .findFirst();

        if (established.isPresent()) {
            return established.get();
        }

        // Затем в ожидающих
        Optional<ConnectionRequestDTO> pending = pendingRequests.values().stream()
                .filter(request -> request.toDeviceIp().equals(deviceIp) &&
                        request.status() == ConnectionRequestDTO.RequestStatus.PENDING)
                .findFirst();

        return pending.orElse(null);
    }

    // ==================== Утилиты ====================

    @Override
    public ConnectionRequestDTO createConnectionRequest(DeviceDTO remoteDevice,
                                                        ConnectionRequestDTO.RequestStatus status) {
        DeviceDTO currentDevice = networkService.getCurrentDevice();

        return new ConnectionRequestDTO(
                currentDevice.name(), currentDevice.ip(),
                remoteDevice.name(), remoteDevice.ip(),
                LocalDateTime.now(), status
        );
    }

    private String generateRequestId(ConnectionRequestDTO request) {
        try {
            InetAddress fromIp = request.getFromDeviceIpAsInetAddress();
            InetAddress toIp = request.getToDeviceIpAsInetAddress();
            return fromIp.getHostAddress() + "->" + toIp.getHostAddress();
        } catch (Exception e) {
            return request.fromDeviceIp() + "->" + request.toDeviceIp();
        }
    }

    // ==================== Уведомления слушателей ====================

    @Override
    public void addListener(ConnectionListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(ConnectionListener listener) {
        listeners.remove(listener);
    }

    private void notifyRequestReceived(ConnectionRequestDTO request) {
        Platform.runLater(() -> {
            for (ConnectionListener listener : listeners) {
                listener.onConnectionRequested(request);
            }
        });
    }

    private void notifyConnectionAccepted(ConnectionRequestDTO request) {
        Platform.runLater(() -> {
            for (ConnectionListener listener : listeners) {
                listener.onConnectionAccepted(request);
            }
        });
    }

    private void notifyConnectionRejected(ConnectionRequestDTO request) {
        Platform.runLater(() -> {
            for (ConnectionListener listener : listeners) {
                listener.onConnectionRejected(request);
            }
        });
    }

    private void notifyConnectionEstablished(ConnectionRequestDTO request) {
        Platform.runLater(() -> {
            for (ConnectionListener listener : listeners) {
                listener.onConnectionEstablished(request);
            }
        });
    }

    private void notifyConnectionDisconnected(String deviceIp) {
        Platform.runLater(() -> {
            for (ConnectionListener listener : listeners) {
                listener.onConnectionDisconnected(deviceIp);
            }
        });
    }
}