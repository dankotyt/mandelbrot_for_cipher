package com.cipher.core.controller.decrypt;

import com.cipher.core.utils.SceneManager;
import com.cipher.core.utils.TempFileManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class DecryptBeginController {

    @FXML private Button uploadButton;
    @FXML private Button backButton;

    private final SceneManager sceneManager;
    private final TempFileManager tempFileManager;

    @FXML
    public void initialize() {
        setupEventHandlers();
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> sceneManager.showStartPanel());
        uploadButton.setOnAction(e -> handleUpload());
    }

    private void handleUpload() {
        String imagePath = tempFileManager.selectImageFileForDecrypt();
        if (imagePath != null) {
            sceneManager.showDecryptLoadPanel(imagePath);
        }
    }
}
