package com.cipher.core.controller.network;

import com.cipher.core.dto.ConnectionRequestDto;
import com.cipher.core.dto.DeviceDTO;
import com.cipher.core.service.network.ConnectionService;
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
public class DevicesController implements ConnectionService.ConnectionListener {
    private static final Logger logger = LoggerFactory.getLogger(DevicesController.class);

    @FXML private Button backButton;
    @FXML private Button refreshButton;
    @FXML private VBox devicesContainer;
    @FXML private StackPane noDevicesPane;
    @FXML private Label statusLabel;
    @FXML private Label currentDeviceLabel;

    private final SceneManager sceneManager;
    private final DialogDisplayer dialogDisplayer;
    private final ConnectionService connectionService;
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
                if (device.getIp().equals(currentDevice.getIp())) {
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
    public void onRequestReceived(ConnectionRequestDto request) {
        // Для Алисы - не используется, так как она только отправляет запросы
    }

    @Override
    public void onRequestAccepted(ConnectionRequestDto request) {
        logger.info("Запрос принят! Подключение установлено с: {}", request.getToDeviceIp());

        updateStatus("Подключение установлено!");

        dialogDisplayer.showAlert("Успех!","Подключение установлено!\n" +
                "IP удаленного устройства: " + request.getToDeviceIp() +
                "\nВаш IP: " + request.getFromDeviceIp());
    }

    @Override
    public void onRequestRejected(ConnectionRequestDto request) {
        logger.info("Запрос отклонен устройством: {}", request.getToDeviceIp());

        updateStatus("Запрос отклонен");

        dialogDisplayer.showAlert("Информация","Запрос на подключение отклонен устройством:\n" + request.getToDeviceName());
    }

    private void updateStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText("Статус: " + status);
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
