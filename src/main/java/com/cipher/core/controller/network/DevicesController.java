package com.cipher.core.controller.network;

import com.cipher.core.dto.DeviceDTO;
import com.cipher.core.dto.connection.ConnectionRequestDTO;
import com.cipher.core.service.network.ConnectionService;
import com.cipher.core.service.network.NetworkService;
import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class DevicesController {
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
    private final ConnectionService connectionService;
    private final NetworkService networkService;

    private List<DeviceDTO> availableDevices;
    private DeviceDTO currentDevice;

    private volatile long lastRequestTime = 0;
    private static final long REQUEST_COOLDOWN_MS = 60000;

    @FXML
    public void initialize() {
        try {
            logger.info("Инициализация DevicesController");

            currentDevice = networkService.getCurrentDevice();
            currentDeviceLabel.setText("Ваше устройство: " + currentDevice);

            setupEventHandlers();
            setupConnectionListener();
            refreshDevices();

            logger.info("DevicesController инициализирован успешно");

        } catch (Exception e) {
            logger.error("Ошибка инициализации DevicesController: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void setupConnectionListener() {
        ConnectionService.ConnectionListener connectionAdapter = new ConnectionService.ConnectionListener() {
            @Override
            public void onRequestReceived(ConnectionRequestDTO request) {
                Platform.runLater(() -> {
                    boolean accepted = dialogDisplayer.showConfirmationDialog(
                            "Запрос на подключение",
                            "Входящее подключение",
                            String.format("Устройство '%s' (%s) хочет подключиться\n\nПринять подключение?",
                                    request.fromDeviceName(), request.fromDeviceIp()),
                            "Принять",
                            "Отклонить"
                    );

                    if (accepted) {
                        connectionService.acceptConnectionRequest(request);
                    } else {
                        connectionService.rejectConnectionRequest(request);
                    }
                });
            }

            @Override
            public void onRequestAccepted(ConnectionRequestDTO request) {
                Platform.runLater(() -> {
                    dialogDisplayer.showSuccessDialog(
                            String.format("Подключение принято!\nУстройство: %s (%s)",
                                    request.fromDeviceName(), request.fromDeviceIp())
                    );
                    DeviceDTO device = new DeviceDTO(request.fromDeviceName(), request.fromDeviceIp());
                    sceneManager.showChatPanel(device);
                });
            }

            @Override
            public void onRequestRejected(ConnectionRequestDTO request) {
                Platform.runLater(() -> {
                    dialogDisplayer.showErrorMessage(
                            String.format("Устройство '%s' отклонило ваш запрос", request.fromDeviceName())
                    );
                });
            }

            @Override
            public void onError(String errorText) {
                Platform.runLater(() -> {
                    dialogDisplayer.showErrorDialog(errorText);
                    updateStatus("❌ " + errorText);
                });
            }
        };

        // Регистрируем адаптер в сервисе
        connectionService.addListener(connectionAdapter);
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> {
            try {
                logger.debug("Нажата кнопка 'Назад'");
                sceneManager.showStartPanel();
            } catch (Exception ex) {
                logger.error("Ошибка при переходе на стартовую панель: {}", ex.getMessage(), ex);
                dialogDisplayer.showErrorDialog("Ошибка перехода: " + ex.getMessage());
            }
        });

        refreshButton.setOnAction(e -> refreshDevices());
        manualIpField.setOnAction(e -> handleManualConnection());
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

            new Thread(() -> {
                try {
                    availableDevices = networkService.discoverLocalDevices();

                    Platform.runLater(() -> {
                        loadDevices();
                        int displayedDevicesCount = countDisplayedDevices();

                        updateStatus("Найдено устройств: " + displayedDevicesCount);
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> {
                        logger.error("Ошибка при обновлении устройств: {}", e.getMessage());
                        updateStatus("Ошибка поиска устройств");
                    });
                }
            }).start();

        } catch (Exception e) {
            logger.error("Ошибка при обновлении устройств: {}", e.getMessage(), e);
            updateStatus("Ошибка поиска устройств");
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
            logger.info("Открытие P2P чата с устройством: {}", device);
            sceneManager.showChatPanel(device);
            updateStatus("Подключение к чату...");

        } catch (Exception e) {
            logger.error("Ошибка при открытии чата: {}", e.getMessage(), e);
            dialogDisplayer.showErrorDialog("Ошибка открытия чата: " + e.getMessage());
        }
    }

    private Button createDeviceButton(DeviceDTO device) {
        HBox buttonContent = new HBox();
        buttonContent.setAlignment(Pos.CENTER_LEFT);
        buttonContent.setSpacing(10);

        Label deviceLabel = new Label(device.toString());
        deviceLabel.getStyleClass().add("device-label");

        Button chatButton = new Button("💬");
        chatButton.getStyleClass().add("chat-button");
        chatButton.setOnAction(e -> handleChatConnection(device));

        buttonContent.getChildren().addAll(deviceLabel, chatButton);

        Button containerButton = new Button();
        containerButton.getStyleClass().add("device-button");
        containerButton.setGraphic(buttonContent);
        containerButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        containerButton.setOnAction(e -> handleDeviceSelection(device));

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
}
