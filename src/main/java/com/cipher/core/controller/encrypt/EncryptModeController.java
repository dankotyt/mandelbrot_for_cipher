package com.cipher.core.controller.encrypt;

import com.cipher.core.utils.SceneManager;
import com.cipher.core.utils.TempFileManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
public class EncryptModeController {
    private final static Logger logger = LoggerFactory.getLogger(EncryptModeController.class);

    @FXML
    private ImageView imageView;
    @FXML
    private Button generateButton;
    @FXML
    private Button manualButton;
    @FXML
    private Button backButton;

    private final SceneManager sceneManager;
    private final TempFileManager tempFileManager;

    @FXML
    public void initialize() {
        loadInputImage();
        setupEventHandlers();
    }

    private void loadInputImage() {
        try {
            ImageView loadedImageView = tempFileManager.loadInputImageFromTemp();
            if (loadedImageView != null && loadedImageView.getImage() != null) {
                imageView.setImage(loadedImageView.getImage());
                //imageView.setPreserveRatio(true); //достаточно использовать это, вместо хард размеров, но могу быть проблемы
            }
        } catch (Exception e) {
            logger.error("Ошибка загрузки изображения", e);
        }
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> sceneManager.showEncryptBeginPanel());
        generateButton.setOnAction(e -> sceneManager.showEncryptGeneratePanel());
        manualButton.setOnAction(e -> sceneManager.showManualEncryptionPanel());
    }
}