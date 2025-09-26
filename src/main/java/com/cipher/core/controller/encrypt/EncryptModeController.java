package com.cipher.core.controller.encrypt;

import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.ImageUtils;
import com.cipher.core.utils.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.awt.image.BufferedImage;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class EncryptModeController {

    @FXML
    private ImageView imageView;
    @FXML
    private Button generateButton;
    @FXML
    private Button manualButton;
    @FXML
    private Button backButton;

    private final SceneManager sceneManager;
    private final ImageUtils imageUtils;
    private final DialogDisplayer dialogDisplayer;

    @FXML
    public void initialize() {
        loadInputImage();
        setupEventHandlers();
    }

    private void loadInputImage() {
        try {
            if (imageUtils.hasOriginalImage()) {
                BufferedImage originalBuffered = imageUtils.getOriginalImage();
                Image originalFx = imageUtils.convertToFxImage(originalBuffered);
                imageView.setImage(originalFx);
            }

        } catch (Exception e) {
            dialogDisplayer.showErrorDialog("Ошибка загрузки изображений");
        }
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> sceneManager.showEncryptBeginPanel());
        generateButton.setOnAction(e -> sceneManager.showEncryptGeneratePanel());
        manualButton.setOnAction(e -> sceneManager.showManualEncryptionPanel());
    }
}