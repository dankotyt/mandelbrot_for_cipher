package com.cipher.core.controller.online;

import com.cipher.core.utils.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AccountController {

    @FXML
    private BorderPane accountContainer;
    @FXML
    private Label titleLabel;
    @FXML
    private Button backButton;

    private final SceneManager sceneManager;

    @FXML
    public void initialize() {
    }

    @FXML
    private void handleBack() {
        sceneManager.showConnectionPanel();
    }

    @FXML
    private void handleLogout() {
        sceneManager.showConnectionPanel();
    }
}
