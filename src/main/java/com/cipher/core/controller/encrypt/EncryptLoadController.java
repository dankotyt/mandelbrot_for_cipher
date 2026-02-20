package com.cipher.core.controller.encrypt;

import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.ImageUtils;
import com.cipher.core.utils.SceneManager;
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

import java.io.File;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class EncryptLoadController {
    private static final Logger logger = LoggerFactory.getLogger(EncryptLoadController.class);
    @FXML private ImageView imageView;
    @FXML private Button continueButton;
    @FXML private Button backButton;

    private final SceneManager sceneManager;
    private final ImageUtils imageUtils;
    private final DialogDisplayer dialogDisplayer;

    @Setter
    private File selectedFile;

    @FXML
    public void initialize() {
        logger.info("EncryptLoadController: ImageUtils instance = {}", imageUtils.hashCode());
        loadInputImage();
        setupEventHandlers();
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> sceneManager.showEncryptBeginPanel());
        continueButton.setOnAction(e -> sceneManager.showEncryptModePanel());
    }

    private void loadInputImage() {
        try {
            if (selectedFile != null && selectedFile.exists()) {
                logger.info("Загрузка изображения из файла: {}", selectedFile.getAbsolutePath());

                // Загружаем изображение через TempFileManager (он уже сохранил в imageUtils)
                Image image = new Image(selectedFile.toURI().toString());
                imageView.setImage(image);

                logger.info("Изображение успешно загружено");
            } else if (imageUtils.hasOriginalImage()) {
                logger.info("Загрузка изображения из imageUtils");
                Image image = imageUtils.getOriginalFXImage();
                imageView.setImage(image);
            } else {
                logger.error("Нет изображения для отображения");
                dialogDisplayer.showErrorDialog("Изображение не было загружено");
            }
        } catch (Exception e) {
            logger.error("Ошибка загрузки изображения: {}", e.getMessage(), e);
            dialogDisplayer.showErrorDialog("Ошибка загрузки изображения: " + e.getMessage());
        }
    }
}