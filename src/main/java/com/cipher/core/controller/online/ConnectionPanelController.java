package com.cipher.core.controller.online;

import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class ConnectionPanelController {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionPanelController.class);

    @FXML private Button backButton;
    @FXML private Button registrationButton;
    @FXML private Button loginButton;

    private final SceneManager sceneManager;
    private final DialogDisplayer dialogDisplayer;

    @FXML
    public void initialize() {
        setupEventHandlers();
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> sceneManager.showStartPanel());
        registrationButton.setOnAction(e -> handleRegistration());
        loginButton.setOnAction(e -> sceneManager.showLoginPanel());
    }

    private void handleRegistration() {
        try {
            sceneManager.showSeedGenerationPanel();
        } catch (Exception e) {
            logger.error("Navigation to registration failed", e);
            dialogDisplayer.showAlert("Ошибка", "Не удалось перейти к регистрации");
        }
    }
}
