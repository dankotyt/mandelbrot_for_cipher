package com.cipher.core.controller.encrypt;

import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import com.cipher.core.utils.TempFileManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class EncryptBeginController {
    private static final Logger logger = LoggerFactory.getLogger(EncryptBeginController.class);

    @FXML private Button backButton;
    @FXML private Button uploadButton;

    private final SceneManager sceneManager;
    private final DialogDisplayer dialogDisplayer;
    private final TempFileManager tempFileManager;

    @FXML
    public void initialize() {
        try {
            logger.info("Инициализация EncryptBeginController");

            if (backButton == null) {
                logger.error("backButton is null! Check FXML file.");
                throw new IllegalStateException("backButton is null! Check FXML file.");
            }
            if (uploadButton == null) {
                logger.error("uploadButton is null! Check FXML file.");
                throw new IllegalStateException("uploadButton is null! Check FXML file.");
            }

            setupEventHandlers();
            logger.info("EncryptBeginController инициализирован успешно");

        } catch (Exception e) {
            logger.error("Ошибка инициализации EncryptBeginController: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> {
            try {
                logger.debug("Нажата кнопка 'Назад'");
                sceneManager.showStartPanel();
            } catch (Exception ex) {
                logger.error("Ошибка при переходе на стартовую панель: {}", ex.getMessage(), ex);
                dialogDisplayer.showErrorDialog("Ошибка перехода: " + ex.getMessage());
            }
        });

        uploadButton.setOnAction(e -> {
            try {
                logger.debug("Нажата кнопка 'Выбрать файл'");
                tempFileManager.selectImageFileForEncrypt();
                sceneManager.showEncryptLoadPanel();
            } catch (Exception ex) {
                logger.error("Ошибка при выборе файла: {}", ex.getMessage(), ex);
                dialogDisplayer.showErrorDialog("Ошибка при выборе файла: " + ex.getMessage());
            }
        });
    }
}
