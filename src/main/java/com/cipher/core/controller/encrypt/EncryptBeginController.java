package com.cipher.core.controller.encrypt;

import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import com.cipher.core.utils.TempFileManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EncryptBeginController {

    @FXML
    private Button backButton;
    @FXML
    private Button uploadButton;

    private final SceneManager sceneManager;
    private final DialogDisplayer dialogDisplayer;
    private final TempFileManager tempFileManager;

    @FXML
    public void initialize() {
        setupEventHandlers();
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> sceneManager.showStartPanel());

        uploadButton.setOnAction(e -> {
            String imagePath = tempFileManager.selectImageFileForEncrypt();
            if (imagePath != null) {
                 sceneManager.showEncryptLoadPanel(imagePath);
            } else {
                dialogDisplayer.showErrorDialog("Файл не выбран!");
            }
        });
    }
}
