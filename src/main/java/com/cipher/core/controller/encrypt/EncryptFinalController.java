package com.cipher.core.controller.encrypt;

import com.cipher.core.dto.neww.EncryptionDataResult;
import com.cipher.core.utils.SceneManager;
import com.cipher.core.utils.TempFileManager;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.awt.image.BufferedImage;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class EncryptFinalController {
    private static final Logger logger = LoggerFactory.getLogger(EncryptFinalController.class);

    @FXML private ImageView imageView;
    @FXML private Button saveButton;
    @FXML private Button backButton;

    private final SceneManager sceneManager;
    private final TempFileManager tempFileManager;

    private BufferedImage encryptedImage;
    @Setter
    private EncryptionDataResult encryptionDataResult;

    public void setEncryptedImage(BufferedImage encryptedImage) {
        this.encryptedImage = encryptedImage;
        displayEncryptedImage();
    }

    @FXML
    public void initialize() {
        setupEventHandlers();
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> handleBack());
        saveButton.setOnAction(e -> handleSave());
    }

    private void displayEncryptedImage() {
        if (encryptedImage != null) {
            Image fxImage = SwingFXUtils.toFXImage(encryptedImage, null);
            imageView.setImage(fxImage);
        }
    }

    private void handleBack() {
        sceneManager.showEncryptChooseAreaPanel();
    }

    private void handleSave() {
        if (encryptedImage != null && encryptionDataResult != null) {
            try {
                tempFileManager.saveEncryptedData(encryptedImage, encryptionDataResult);

                sceneManager.showStartPanel();

            } catch (Exception e) {
                logger.error("Ошибка сохранения", e);
            }
        }
    }
}
