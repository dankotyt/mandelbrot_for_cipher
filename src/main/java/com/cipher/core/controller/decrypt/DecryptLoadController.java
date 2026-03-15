package com.cipher.core.controller.decrypt;

import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.ImageUtils;
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

import java.awt.image.BufferedImage;

@Deprecated
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
    private final ImageUtils imageUtils;

    @FXML
    public void initialize() {
        logger.info("DecryptLoadController: ImageUtils instance = {}", imageUtils.hashCode());
        loadImage();
        setupEventHandlers();
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> sceneManager.showDecryptBeginPanel());
        continueButton.setOnAction(e -> sceneManager.showDecryptModePanel());
    }

    private void loadImage() {
        try {
            logger.info("Начало загрузки изображения");

            if (imageUtils.hasOriginalImage()) {
                logger.info("Изображение найдено в imageUtils");
                BufferedImage originalBuffered = imageUtils.getOriginalImage();
                logger.info("BufferedImage получен: {}x{}",
                        originalBuffered.getWidth(), originalBuffered.getHeight());

                Image originalFx = imageUtils.convertToFxImage(originalBuffered);
                logger.info("FX Image создан");

                imageView.setImage(originalFx);
                logger.info("Изображение установлено в ImageView");
            } else {
                logger.error("Нет оригинального изображения в imageUtils");
                dialogDisplayer.showErrorDialog("Изображение не было загружено");
            }

        } catch (Exception e) {
            logger.error("Ошибка загрузки изображения: {}", e.getMessage(), e);
            dialogDisplayer.showErrorDialog("Ошибка загрузки изображения: " + e.getMessage());
        }
    }
}
