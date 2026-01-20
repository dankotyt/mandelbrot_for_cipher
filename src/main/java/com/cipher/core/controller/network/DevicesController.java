package com.cipher.core.controller.network;

import com.cipher.core.dto.connection.ConnectionRequestDTO;
import com.cipher.core.dto.DeviceDTO;
import com.cipher.core.listener.DeviceDiscoveryEventListener;
import com.cipher.core.service.network.KeyExchangeService;
import com.cipher.core.service.network.NetworkVisibilityService;
import com.cipher.core.service.network.impl.ConnectionServiceImpl;
import com.cipher.core.service.network.impl.NetworkServiceImpl;
import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class DevicesController implements ConnectionServiceImpl.ConnectionListener {
    private static final Logger logger = LoggerFactory.getLogger(DevicesController.class);

    @FXML private Button backButton;
    @FXML private Button refreshButton;
    @FXML private VBox devicesContainer;
    @FXML private StackPane noDevicesPane;
    @FXML private Label statusLabel;
    @FXML private Label currentDeviceLabel;
    @FXML private TextField manualIpField;
    @FXML private Button manualConnectButton;

    private final SceneManager sceneManager;
    private final DialogDisplayer dialogDisplayer;
    private final ConnectionServiceImpl connectionService;
    private final NetworkServiceImpl networkService;
    private final KeyExchangeService keyExchangeService;
    private final DeviceDiscoveryEventListener deviceEventListener;
    private final NetworkVisibilityService networkVisibilityService;

    private List<DeviceDTO> availableDevices;
    private DeviceDTO currentDevice;
    private String discoverySubscriptionId;
    private String lostSubscriptionId;

    private final Map<String, DeviceDTO> deviceMap = new ConcurrentHashMap<>();

    private volatile long lastRequestTime = 0;
    private static final long REQUEST_COOLDOWN_MS = 60000;

    @FXML
    public void initialize() {
        try {
            logger.info("Инициализация DevicesController");

            // СТАНОВИМСЯ ВИДИМЫМИ В СЕТИ
            networkVisibilityService.becomeVisible();

            currentDevice = networkService.getCurrentDevice();
            currentDeviceLabel.setText("Ваше устройство: " + currentDevice);

            setupEventHandlers();
            connectionService.addListener(this);

            subscribeToDeviceEvents();

            // Первоначальная загрузка устройств
            refreshDevices();

            logger.info("DevicesController инициализирован успешно");

        } catch (Exception e) {
            logger.error("Ошибка инициализации DevicesController: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> {
            try {
//                boolean confirmed = dialogDisplayer.showConfirmationDialog(
//                        "Выход из локальной сети",
//                        "Вы уверены, что хотите выйти из локальной сети?",
//                        "Ваше устройство станет невидимым для других устройств.\n" +
//                                "Все подключения будут разорваны.",
//                        "Выйти",
//                        "Отмена"
//                );
//
//                if (confirmed) {
//
//                }
                cleanup();

                sceneManager.showStartPanel();

                logger.info("Пользователь вышел из локальной сети");
            } catch (Exception ex) {
                logger.error("Ошибка при выходе из локальной сети: {}", ex.getMessage(), ex);
                dialogDisplayer.showErrorDialog("Ошибка выхода: " + ex.getMessage());
            }
        });

        refreshButton.setOnAction(e -> refreshDevices());
        manualIpField.setOnAction(e -> handleManualConnection());
    }

    private void subscribeToDeviceEvents() {
        // Подписка на обнаружение устройств
        discoverySubscriptionId = deviceEventListener.subscribeToDiscovery(device -> {
            logger.info("🔄 Получено новое устройство: {}", device);

            if (isSelfIpAddress(device.ip())) {
                return;
            }

            // Просто добавляем устройство
            deviceMap.put(device.ip(), device);
            updateDeviceListUI();

            Platform.runLater(() -> {
                updateStatus("Найдено устройство: " + device.name());
            });
        });

        // Подписка на потерю устройств
        lostSubscriptionId = deviceEventListener.subscribeToLost(address -> {
            logger.info("🔴 Устройство потеряно (goodbye или таймаут): {}",
                    address.getHostAddress());

            String ip = address.getHostAddress();
            deviceMap.remove(ip);
            updateDeviceListUI();

            Platform.runLater(() -> {
                updateStatus("Устройство отключено: " + address.getHostAddress());
            });
        });
    }

    private void updateDeviceListUI() {
        Platform.runLater(() -> {
            // Обновляем availableDevices из карты
            availableDevices = new ArrayList<>(deviceMap.values());

            // Сортируем по имени
            availableDevices.sort(Comparator.comparing(DeviceDTO::name));

            // Перерисовываем список
            loadDevices();

            logger.debug("UI обновлен, устройств: {}", availableDevices.size());
        });
    }

    @FXML
    private void handleManualConnection() {
        String ip = manualIpField.getText().trim();

        if (isOnCooldown()) {
            long remainingSeconds = getRemainingCooldownSeconds();
            dialogDisplayer.showTimedErrorAlert("Ошибка",
                    "Слишком частые запросы\nПопробуйте через " + remainingSeconds + " сек.",
                    3
            );
            updateStatus("Подождите перед следующим запросом");
            return;
        }

        if (ip.isEmpty()) {
            dialogDisplayer.showTimedErrorAlert("Ошибка", "Введите IP адрес", 3);
            return;
        }

        if (!isValidIpAddress(ip)) {
            dialogDisplayer.showTimedErrorAlert("Ошибка", "Неверный формат IP адреса", 3);
            return;
        }

        if (isSelfIpAddress(ip)) {
            dialogDisplayer.showTimedErrorAlert("Ошибка", "Нельзя отправить запрос самому себе", 3);
            updateStatus("Ошибка: запрос самому себе");
            return;
        }

        updateStatus("Проверка устройства...");

        new Thread(() -> {
            try {
                boolean isReachable = networkService.isAppRunning(ip);

                Platform.runLater(() -> {
                    if (isReachable) {
                        lastRequestTime = System.currentTimeMillis();

                        DeviceDTO manualDevice = new DeviceDTO("Ручное подключение", ip);
                        addDeviceToContainer(manualDevice);
                        manualIpField.clear();
                        updateStatus("Устройство добавлено");

                        try {
                            connectionService.sendConnectionRequest(manualDevice);
                            dialogDisplayer.showTimedAlert("Успех",
                                    "Запрос на подключение отправлен устройству:\n" + ip, 5);
                        } catch (Exception e) {
                            logger.error("Ошибка при отправке запроса: {}", e.getMessage(), e);
                            dialogDisplayer.showTimedErrorAlert("Ошибка",
                                    "Не удалось отправить запрос: " + e.getMessage(), 3);
                        }

                    } else {
                        updateStatus("Устройство не доступно");
                        dialogDisplayer.showTimedErrorAlert("Ошибка",
                                "Устройство не доступно\n" +
                                        "IP: " + ip + "\n" +
                                        "Убедитесь, что:\n" +
                                        "• Устройство в сети\n" +
                                        "• Программа запущена\n" +
                                        "• Порт 25565 открыт",
                                5
                        );
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    updateStatus("Ошибка проверки");
                    dialogDisplayer.showTimedErrorAlert("Ошибка", "Ошибка проверки: " + e.getMessage(), 3);
                });
            }
        }).start();
    }

    private boolean isValidIpAddress(String ip) {
        try {
            if (ip.equals("localhost") || ip.equals("127.0.0.1")) {
                return true;
            }

            String[] parts = ip.split("\\.");
            if (parts.length != 4) return false;

            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void addDeviceToContainer(DeviceDTO device) {
        for (Node node : devicesContainer.getChildren()) {
            if (node instanceof Button existingButton) {
                if (existingButton.getText().contains(device.ip())) {
                    dialogDisplayer.showAlert("Информация", "Устройство уже в списке");
                    return;
                }
            }
        }

        Button deviceButton = createDeviceButton(device);
        devicesContainer.getChildren().add(deviceButton);
        hideNoDevicesMessage();
    }

    private void refreshDevices() {
        try {
            updateStatus("Поиск устройств...");
            logger.info("Начато обновление списка устройств");

            PauseTransition pause = new PauseTransition(Duration.millis(500));
            pause.setOnFinished(event -> {
                Platform.runLater(() -> {
                    try {
                        List<DeviceDTO> discoveredDevices = networkService.getDiscoveredDevices();

                        synchronized (deviceMap) {
                            // Очищаем старые записи, которые больше не обнаружены
                            Set<String> currentIps = discoveredDevices.stream()
                                    .map(DeviceDTO::ip)
                                    .collect(Collectors.toSet());

                            // Удаляем устройства, которые больше не обнаружены
                            List<String> toRemove = new ArrayList<>();
                            for (String ip : deviceMap.keySet()) {
                                if (!currentIps.contains(ip) && !isSelfIpAddress(ip)) {
                                    toRemove.add(ip);
                                }
                            }

                            for (String ip : toRemove) {
                                deviceMap.remove(ip);
                                logger.debug("Удалено устройство (больше не обнаружено): {}", ip);
                            }

                            // Добавляем новые обнаруженные устройства
                            for (DeviceDTO device : discoveredDevices) {
                                if (!isSelfIpAddress(device.ip())) {
                                    deviceMap.put(device.ip(), device);
                                }
                            }
                        }

                        // Обновляем UI
                        updateDeviceListUI();

                        // 3. Теперь показываем результат поиска
                        int count = deviceMap.size();
                        if (count == 0) {
                            updateStatus("Устройства не найдены");
                        } else {
                            updateStatus("Найдено устройств: " + count);
                        }

                        // Проверяем входящие запросы
                        connectionService.checkIncomingRequests();

                        logger.info("Список устройств обновлен. Устройств: {}", count);

                    } catch (Exception e) {
                        logger.error("Ошибка при обновлении устройств: {}", e.getMessage(), e);
                        updateStatus("Ошибка обновления списка");
                    }
                });
            });

            // Запускаем паузу
            pause.play();

        } catch (Exception e) {
            logger.error("Ошибка при обновлении устройств: {}", e.getMessage(), e);
            updateStatus("Ошибка обновления списка");
        }
    }

    private int countDisplayedDevices() {
        if (availableDevices == null || availableDevices.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (DeviceDTO device : availableDevices) {
            if (!device.ip().equals(currentDevice.ip())) {
                count++;
            }
        }
        return count;
    }

    private void loadDevices() {
        try {
            devicesContainer.getChildren().clear();

            if (availableDevices.isEmpty()) {
                showNoDevicesMessage();
                return;
            }

            hideNoDevicesMessage();

            for (DeviceDTO device : availableDevices) {
                // Пропускаем текущее устройство
                if (device.ip().equals(currentDevice.ip())) {
                    continue;
                }

                Button deviceButton = createDeviceButton(device);
                devicesContainer.getChildren().add(deviceButton);
            }

            logger.info("Загружено {} устройств", availableDevices.size());

        } catch (Exception e) {
            logger.error("Ошибка при загрузке устройств: {}", e.getMessage(), e);
            dialogDisplayer.showErrorDialog("Ошибка при загрузке устройств: " + e.getMessage());
        }
    }

    private void handleChatConnection(DeviceDTO device) {
        try {
            if (isSelfDevice(device)) {
                dialogDisplayer.showAlert("Информация", "Нельзя открыть чат с самим собой");
                return;
            }

            if (!deviceMap.containsKey(device.ip())) {
                updateStatus("Устройство больше не доступно");
                dialogDisplayer.showTimedErrorAlert("Ошибка",
                        "Устройство больше не доступно:\n" +
                                device.name() + " (" + device.ip() + ")\n\n" +
                                "Устройство вышло из локальной сети",
                        5
                );
                return;
            }

            if (isOnCooldown()) {
                long remainingSeconds = getRemainingCooldownSeconds();
                dialogDisplayer.showTimedErrorAlert("Ошибка",
                        "Слишком частые запросы\nПопробуйте через " + remainingSeconds + " сек.",
                        3
                );
                updateStatus("Подождите перед следующим запросом");
                return;
            }

            // ПРОВЕРЯЕМ ДОСТУПНОСТЬ УСТРОЙСТВА ПЕРЕД ОТПРАВКОЙ
            updateStatus("Проверка доступности устройства...");

            new Thread(() -> {
                boolean isReachable = networkService.isAppRunning(device.ip());

                Platform.runLater(() -> {
                    if (!isReachable) {
                        updateStatus("Устройство недоступно");
                        dialogDisplayer.showTimedErrorAlert("Ошибка",
                                "Устройство недоступно:\n" +
                                        device.name() + " (" + device.ip() + ")\n\n" +
                                        "Убедитесь, что программа запущена на удаленном устройстве",
                                5
                        );

                        // Удаляем устройство из списка
                        deviceMap.remove(device.ip());
                        updateDeviceListUI();

                        return;
                    }

                    // Устройство доступно - продолжаем
                    logger.info("Отправка запроса на подключение и открытие чата с: {}", device);
                    updateStatus("Отправка запроса...");

                    // Сразу открываем чат (ожидающий подключения)
                    sceneManager.showChatPanel(device);

                    // Отправляем запрос на подключение
                    connectionService.sendConnectionRequest(device);

                    updateStatus("Запрос отправлен");
                });
            }).start();

        } catch (Exception e) {
            logger.error("Ошибка при открытии чата: {}", e.getMessage(), e);
            dialogDisplayer.showErrorDialog("Ошибка открытия чата: " + e.getMessage());
            updateStatus("Ошибка отправки");
        }
    }

    private Button createDeviceButton(DeviceDTO device) {
        HBox buttonContent = new HBox();
        buttonContent.setAlignment(Pos.CENTER_LEFT);
        buttonContent.setSpacing(10);

        Label deviceLabel = new Label(device.toString());
        deviceLabel.getStyleClass().add("device-label");

        HBox buttonsContainer = new HBox();
        buttonsContainer.setAlignment(Pos.CENTER_RIGHT);
        buttonsContainer.setSpacing(5);

        // УБИРАЕМ кнопку "Подключиться", оставляем только "Чат"
        Button chatButton = new Button("💬 Чат");
        chatButton.getStyleClass().add("chat-button");
        chatButton.setOnAction(e -> handleChatConnection(device));

        buttonsContainer.getChildren().add(chatButton);

        buttonContent.getChildren().addAll(deviceLabel, buttonsContainer);
        HBox.setHgrow(deviceLabel, Priority.ALWAYS);

        Button containerButton = new Button();
        containerButton.getStyleClass().add("device-button");
        containerButton.setGraphic(buttonContent);
        containerButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        containerButton.setMaxWidth(Double.MAX_VALUE);

        return containerButton;
    }

    private void handleDeviceSelection(DeviceDTO device) {
        try {
            if (isSelfDevice(device)) {
                dialogDisplayer.showAlert("Информация","Нельзя отправить запрос самому себе");
                updateStatus("Ошибка: запрос самому себе");
                return;
            }

            if (isOnCooldown()) {
                long remainingSeconds = getRemainingCooldownSeconds();
                dialogDisplayer.showTimedErrorAlert("Ошибка",
                        "Слишком частые запросы\nПопробуйте через " + remainingSeconds + " сек.",
                        3
                );
                updateStatus("Подождите перед следующим запросом");
                return;
            }

            logger.debug("Отправка запроса на подключение к устройству: {}", device);

            updateStatus("Отправка запроса...");

            connectionService.sendConnectionRequest(device);

            dialogDisplayer.showTimedAlert("Информация","Запрос отправлен устройству: " + device, 5);
        } catch (Exception e) {
            logger.error("Ошибка при отправке запроса: {}", e.getMessage(), e);
            dialogDisplayer.showErrorDialog("Ошибка при отправке запроса: " + e.getMessage());
            updateStatus("Ошибка отправки");
        }
    }

    private boolean isSelfDevice(DeviceDTO device) {
        return device.ip().equals(currentDevice.ip());
    }

    private boolean isSelfIpAddress(String ip) {
        try {
            String localIp = networkService.getLocalIpAddress();
            return ip.equals(localIp) ||
                    ip.equals("127.0.0.1") ||
                    ip.equals("localhost");
        } catch (Exception e) {
            logger.error("Ошибка при проверке IP: {}", e.getMessage());
            return false;
        }
    }

    private boolean isOnCooldown() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastRequestTime;
        return timeSinceLastRequest < REQUEST_COOLDOWN_MS;
    }

    private long getRemainingCooldownSeconds() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastRequestTime;
        long remainingMs = REQUEST_COOLDOWN_MS - timeSinceLastRequest;
        return (remainingMs / 1000) + 1;
    }

    @Override
    public void onRequestReceived(ConnectionRequestDTO request) {
        // Для Алисы - не используется, так как она только отправляет запросы
    }

    @Override
    public void onRequestAccepted(ConnectionRequestDTO request) {
        logger.info("Запрос принят! Подключение установлено с: {}", request.toDeviceIp());

        updateStatus("Подключение установлено!");

        dialogDisplayer.showAlert("Успех!","Подключение установлено!\n" +
                "IP удаленного устройства: " + request.toDeviceIp() +
                "\nВаш IP: " + request.fromDeviceIp());
    }

    @Override
    public void onRequestRejected(ConnectionRequestDTO request) {
        logger.info("Запрос отклонен устройством: {}", request.toDeviceIp());

        updateStatus("Запрос отклонен");

        dialogDisplayer.showAlert("Информация","Запрос на подключение отклонен устройством:\n" + request.toDeviceName());
    }

    private void updateStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText("Статус: " + status);

            statusLabel.getStyleClass().removeAll(
                    "status-waiting", "status-searching", "status-success",
                    "status-error", "status-connected"
            );

            if (status.contains("Ожидание") || status.contains("ожидание")) {
                statusLabel.getStyleClass().add("status-waiting");
            } else if (status.contains("Поиск") || status.contains("поиск")) {
                statusLabel.getStyleClass().add("status-searching");
            } else if (status.contains("Успех") || status.contains("установлено")) {
                statusLabel.getStyleClass().add("status-success");
            } else if (status.contains("Ошибка") || status.contains("ошибка")) {
                statusLabel.getStyleClass().add("status-error");
            } else if (status.contains("Подключение") || status.contains("подключен")) {
                statusLabel.getStyleClass().add("status-connected");
            }
        }
    }

    private void showNoDevicesMessage() {
        noDevicesPane.setVisible(true);
        devicesContainer.setVisible(false);
    }

    private void hideNoDevicesMessage() {
        noDevicesPane.setVisible(false);
        devicesContainer.setVisible(true);
    }

    public void cleanup() {
        logger.info("Начало очистки DevicesController...");

        try {
            // 1. Становимся невидимыми
            networkVisibilityService.becomeInvisible();
            logger.info("Устройство стало невидимым");

            // 2. Отписываемся от соединений
            connectionService.removeListener(this);
            logger.info("Отписались от ConnectionService");

            // 3. Отписываемся от событий обнаружения
            if (discoverySubscriptionId != null) {
                deviceEventListener.unsubscribe(discoverySubscriptionId);
                logger.info("Отписались от событий обнаружения: {}", discoverySubscriptionId);
            }
            if (lostSubscriptionId != null) {
                deviceEventListener.unsubscribe(lostSubscriptionId);
                logger.info("Отписались от событий потери: {}", lostSubscriptionId);
            }

            // 4. Очищаем данные
            deviceMap.clear();
            availableDevices.clear();
            logger.info("Данные очищены");

            logger.info("DevicesController полностью очищен");

        } catch (Exception e) {
            logger.error("Ошибка при очистке DevicesController: {}", e.getMessage(), e);
        }
    }
}
