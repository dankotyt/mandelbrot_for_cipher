package com.cipher.core.controller.decrypt;

import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.scene.image.Image;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.io.File;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class DecryptLoadController {

    private static final Logger logger = LoggerFactory.getLogger(DecryptLoadController.class);

    @FXML private ImageView imageView;
    @FXML private Button continueButton;
    @FXML private Button backButton;

    private final SceneManager sceneManager;
    private final DialogDisplayer dialogDisplayer;

    private String imagePath;

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
        loadImage();
    }

    @FXML
    public void initialize() {
        setupEventHandlers();
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> sceneManager.showDecryptBeginPanel());
        continueButton.setOnAction(e -> sceneManager.showDecryptModePanel());
    }

    private void loadImage() {
        if (imagePath == null || imagePath.isEmpty()) {
            dialogDisplayer.showErrorDialog("Путь к изображению не указан");
            return;
        }

        try {
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                dialogDisplayer.showErrorDialog("Файл не существует: " + imagePath);
                return;
            }

            Image image = new Image("file:" + imagePath);
            imageView.setImage(image);

        } catch (Exception e) {
            logger.error("Ошибка загрузки изображения: {}", imagePath, e);
            dialogDisplayer.showErrorDialog("Не удалось загрузить изображение: " + e.getMessage());
        }
    }
}
