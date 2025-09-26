package com.cipher.core.controller.decrypt;

import com.cipher.core.encryption.ImageDecrypt;
import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import com.cipher.core.utils.TempFileManager;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class DecryptFinalController {
    private final static Logger logger = LoggerFactory.getLogger(DecryptFinalController.class);

    @FXML private StackPane imageContainer;
    @FXML private Button saveButton;
    @FXML private Button backButton;

    private final SceneManager sceneManager;
    private final TempFileManager tempFileManager;
    private final DialogDisplayer dialogDisplayer;
    private final ImageDecrypt imageDecrypt;
    private String keyFilePath;

    public void setKeyFilePath(String keyFilePath) {
        this.keyFilePath = keyFilePath;
        startDecryption();
    }

    @FXML
    public void initialize() {
        setupEventHandlers();
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> sceneManager.showStartPanel());
        saveButton.setOnAction(e -> tempFileManager.saveDecryptedImage());
    }

    private void startDecryption() {
        if (keyFilePath == null || keyFilePath.isEmpty()) {
            dialogDisplayer.showErrorDialog("Путь к файлу-ключу не указан");
            return;
        }

        Task<Void> decryptImageTask = new Task<>() {
            @Override
            protected Void call() {
                imageDecrypt.decryptImage(keyFilePath);
                return null;
            }
        };

        decryptImageTask.setOnSucceeded(e -> {
            try {
                displayDecryptedImage();
            } catch (Exception ex) {
                logger.error("Ошибка при загрузке расшифрованного изображения", ex);
                //dialogDisplayer.showErrorDialog("Ошибка при загрузке изображения: " + ex.getMessage());
            }
        });

        decryptImageTask.setOnFailed(e -> {
            logger.error("Ошибка при расшифровке изображения", decryptImageTask.getException());
            dialogDisplayer.showErrorDialog("Ошибка при расшифровке: " + decryptImageTask.getException().getMessage());
        });

        new Thread(decryptImageTask).start();
    }

    private void displayDecryptedImage() {
        try {
            Image decryptedImage = tempFileManager.loadImageFromTemp("decrypted_image.png");
            if (decryptedImage != null) {
                ImageView imageView = new ImageView(decryptedImage);

                imageContainer.getChildren().clear();
                imageContainer.getChildren().add(imageView);
            }
        } catch (Exception e) {
            logger.error("Ошибка отображения расшифрованного изображения", e);
            //dialogDisplayer.showErrorDialog("Ошибка отображения изображения: " + e.getMessage());
        }
    }
}
