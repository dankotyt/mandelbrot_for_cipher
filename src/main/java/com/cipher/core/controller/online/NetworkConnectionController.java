package com.cipher.core.controller.online;

import com.cipher.core.utils.NetworkManager;
import com.cipher.core.utils.SceneManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NetworkConnectionController {

    @FXML private Label statusLabel;
    @FXML private ListView<String> devicesListView;
    @FXML private Button refreshButton;
    @FXML private Button connectButton;
    @FXML private Button backButton;
    @FXML private Button generateKeysButton;
    @FXML private TextArea connectionInfoArea;
    @FXML private VBox connectionInfoBox;

    private final NetworkManager networkManager;
    private final SceneManager sceneManager;

    private Map<InetAddress, Long> currentDevices;

    @FXML
    public void initialize() {
        setupEventHandlers();
        startNetworkDiscovery();
    }

    private void setupEventHandlers() {
        refreshButton.setOnAction(e -> refreshDevices());
        connectButton.setOnAction(e -> connectToSelectedDevice());
        backButton.setOnAction(e -> sceneManager.showStartPanel());
        generateKeysButton.setOnAction(e -> generateNewKeys());

        devicesListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> connectButton.setDisable(newVal == null)
        );
    }

    private void startNetworkDiscovery() {
        networkManager.startNetworkServices(new NetworkManager.DevicesUpdateCallback() {
            @Override
            public void onDevicesUpdated(Map<InetAddress, Long> devices) {
                updateDevicesList(devices);
            }

            @Override
            public void onConnectionStatusChanged(String status) {
                updateStatus(status);
            }

            @Override
            public void onError(String error) {
                showError(error);
            }

            @Override
            public void onDeviceDiscovered(InetAddress address) {
                Platform.runLater(() -> {
                    connectionInfoArea.appendText("✓ Обнаружено устройство: " + address.getHostAddress() + "\n");
                });
            }

            @Override
            public void onDeviceLost(InetAddress address) {
                Platform.runLater(() -> {
                    connectionInfoArea.appendText("✗ Устройство пропало: " + address.getHostAddress() + "\n");
                });
            }
        });
    }

    private void updateDevicesList(Map<InetAddress, Long> devices) {
        currentDevices = devices;

        Platform.runLater(() -> {
            devicesListView.getItems().clear();

            for (InetAddress address : devices.keySet()) {
                devicesListView.getItems().add(address.getHostAddress());
            }

            statusLabel.setText("Найдено устройств: " + devices.size());
        });
    }

    private void updateStatus(String status) {
        javafx.application.Platform.runLater(() -> {
            statusLabel.setText(status);
            connectionInfoArea.appendText(status + "\n");
            connectionInfoBox.setVisible(true);
        });
    }

    private void showError(String error) {
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText("Сетевая ошибка");
            alert.setContentText(error);
            alert.showAndWait();
        });
    }

    private void refreshDevices() {
        networkManager.getDiscoveredDevices();
        updateStatus("Обновление списка устройств...");
    }

    private void connectToSelectedDevice() {
        String selectedAddress = devicesListView.getSelectionModel().getSelectedItem();
        if (selectedAddress != null) {
            try {
                InetAddress address = InetAddress.getByName(selectedAddress);
                networkManager.connectToDevice(address);
            } catch (Exception e) {
                showError("Неверный IP адрес: " + e.getMessage());
            }
        }
    }

    private void generateNewKeys() {
        // Здесь будет логика генерации новых ключей
        updateStatus("Генерация новых ключей...");
        // keyExchangeService.generateNewKeys();
        updateStatus("Новые ключи сгенерированы");
    }

    @FXML
    private void handleBack() {
        sceneManager.showStartPanel();
    }

    @FXML
    private void cleanup() {
        networkManager.stopNetworkServices();
    }
}
