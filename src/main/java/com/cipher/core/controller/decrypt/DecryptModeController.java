package com.cipher.core.controller.decrypt;

import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import com.cipher.core.utils.TempFileManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.io.File;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class DecryptModeController {
    private static final Logger logger = LoggerFactory.getLogger(DecryptModeController.class);

    @FXML private ImageView inputImageView;
    @FXML private Button manualButton;
    @FXML private Button backButton;

    private final SceneManager sceneManager;
    private final TempFileManager tempFileManager;
    private final DialogDisplayer dialogDisplayer;

    @FXML
    public void initialize() {
        loadInputImage();
        setupEventHandlers();
    }

    private void loadInputImage() {
        try {
            ImageView inputImage = tempFileManager.loadInputImageFromTemp();
            if (inputImage != null && inputImage.getImage() != null) {
                inputImageView.setImage(inputImage.getImage());
            } else {
                logger.warn("Input image not found in temp folder");
            }
        } catch (Exception e) {
            logger.error("Ошибка загрузки входного изображения", e);
            dialogDisplayer.showErrorDialog("Ошибка загрузки изображения: " + e.getMessage());
        }
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> sceneManager.showDecryptBeginPanel());
        manualButton.setOnAction(e -> handleLoadKeyFile());
    }

    private void handleLoadKeyFile() {
        File selectedFile = tempFileManager.selectKeyFileForDecrypt();
        if (selectedFile != null) {
            sceneManager.showDecryptFinalPanel(selectedFile.getAbsolutePath());
        } else {
            dialogDisplayer.showErrorDialog("Файл-ключ не выбран!");
        }
    }
}