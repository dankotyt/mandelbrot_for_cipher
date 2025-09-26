package com.cipher.core.controller.online;

import com.cipher.core.utils.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class AccountPanelController {

    @FXML private Button backButton;

    private final SceneManager sceneManager;

    @FXML
    public void initialize() {
        backButton.setOnAction(e -> handleBack());
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
