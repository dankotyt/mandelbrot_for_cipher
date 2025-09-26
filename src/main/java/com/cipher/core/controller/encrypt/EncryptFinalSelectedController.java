package com.cipher.core.controller.encrypt;

import com.cipher.core.utils.SceneManager;
import com.cipher.core.utils.TempFileManager;

import javafx.embed.swing.SwingFXUtils;
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
public class EncryptFinalSelectedController {

    @FXML private ImageView imageView;
    @FXML private Button saveButton;
    @FXML private Button backButton;

    private final SceneManager sceneManager;
    private final TempFileManager tempFileManager;
    private BufferedImage encryptedImage;

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
        saveButton.setOnAction(e -> handleSaveAndExit());
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

    private void handleSaveAndExit() {
        if (encryptedImage != null) {
            tempFileManager.saveEncryptedImage(encryptedImage);
            sceneManager.showStartPanel();
        }
    }
}
