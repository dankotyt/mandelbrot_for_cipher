package com.cipher.core.controller.network;

import com.cipher.core.dto.ConnectionRequestDTO;
import com.cipher.core.dto.DeviceDTO;
import com.cipher.core.service.network.ConnectionServiceImpl;
import com.cipher.core.service.network.NetworkService;
import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
public class DevicesController implements ConnectionServiceImpl.ConnectionListener {
    private static final Logger logger = LoggerFactory.getLogger(DevicesController.class);

    @FXML private Button backButton;
    @FXML private Button refreshButton;
    @FXML private VBox devicesContainer;
    @FXML private StackPane noDevicesPane;
    @FXML private Label statusLabel;
    @FXML private Label currentDeviceLabel;

    private final SceneManager sceneManager;
    private final DialogDisplayer dialogDisplayer;
    private final ConnectionServiceImpl connectionService;
    private final NetworkService networkService;

    private List<DeviceDTO> availableDevices;
    private DeviceDTO currentDevice;

    @FXML
    public void initialize() {
        try {
            logger.info("Инициализация DevicesController");

            // Получаем информацию о текущем устройстве
            currentDevice = networkService.getCurrentDevice();
            currentDeviceLabel.setText("Ваше устройство: " + currentDevice);

            setupEventHandlers();
            connectionService.addListener(this);
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
                connectionService.removeListener(this);
                logger.debug("Нажата кнопка 'Назад'");
                sceneManager.showStartPanel();
            } catch (Exception ex) {
                logger.error("Ошибка при переходе на стартовую панель: {}", ex.getMessage(), ex);
                dialogDisplayer.showErrorDialog("Ошибка перехода: " + ex.getMessage());
            }
        });

        refreshButton.setOnAction(e -> refreshDevices());
    }

    private void refreshDevices() {
        try {
            updateStatus("Поиск устройств...");

            new Thread(() -> {
                try {
                    availableDevices = networkService.discoverLocalDevices();

                    Platform.runLater(() -> {
                        loadDevices();
                        updateStatus("Найдено устройств: " + availableDevices.size());

                        // Проверяем входящие запросы
                        connectionService.checkIncomingRequests();
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

    private Button createDeviceButton(DeviceDTO device) {
        Button button = new Button();
        button.getStyleClass().add("device-button");
        button.setText(device.toString());
        button.setOnAction(e -> handleDeviceSelection(device));
        return button;
    }

    private void handleDeviceSelection(DeviceDTO device) {
        try {
            logger.debug("Отправка запроса на подключение к устройству: {}", device);

            updateStatus("Отправка запроса...");

            connectionService.sendConnectionRequest(device);

            dialogDisplayer.showAlert("Информация","Запрос на подключение отправлен устройству:\n" + device);

        } catch (Exception e) {
            logger.error("Ошибка при отправке запроса: {}", e.getMessage(), e);
            dialogDisplayer.showErrorDialog("Ошибка при отправке запроса: " + e.getMessage());
            updateStatus("Ошибка отправки");
        }
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
}
