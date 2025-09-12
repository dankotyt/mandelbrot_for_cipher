package com.cipher.core.controller.online;

import com.cipher.client.service.impl.SeedServiceImpl;
import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
public class ConnectionController {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionController.class);

    @FXML
    private Button backButton;
    @FXML
    private Button registrationButton;
    @FXML
    private Button loginButton;
    @FXML
    private Label titleLabel;

    private final SeedServiceImpl seedService;
    private final SceneManager sceneManager;
    private final DialogDisplayer dialogDisplayer;

    @FXML
    public void initialize() {

    }

    private void setupStyles() {
        backButton.getStyleClass().add("icon-back-button");
        registrationButton.getStyleClass().add("connection-button");
        loginButton.getStyleClass().add("connection-button");
        titleLabel.getStyleClass().add("connection-title");
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> sceneManager.showStartPanel());
        registrationButton.setOnAction(e -> handleRegistration());
        loginButton.setOnAction(e -> sceneManager.showLoginPanel());
    }

    private void handleRegistration() {
        try {
            if (seedService != null) {
                String seedPhrase = seedService.generateAccount();
                sceneManager.showSeedGenerationPanel(seedPhrase);
            } else {
                dialogDisplayer.showAlert("Ошибка", "Сервис не инициализирован!");
            }
        } catch (Exception e) {
            logger.error("Registration failed", e);
            dialogDisplayer.showAlert("Ошибка", "Не удалось создать аккаунт: " + e.getMessage());
        }
    }
}
