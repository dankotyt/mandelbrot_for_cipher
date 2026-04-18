package com.dankotyt.core.controller.decrypt;

import com.dankotyt.core.utils.DialogDisplayer;
import com.dankotyt.core.utils.SceneManager;
import com.dankotyt.core.utils.FileManager;
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
public class DecryptBeginController {
    private static final Logger logger = LoggerFactory.getLogger(DecryptBeginController.class);

    @FXML private Button uploadButton;
    @FXML private Button backButton;

    private final SceneManager sceneManager;
    private final FileManager fileManager;
    private final DialogDisplayer dialogDisplayer;

    @FXML
    public void initialize() {
        setupEventHandlers();
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> {
            try {
                logger.debug("Нажата кнопка 'Назад'");
                sceneManager.returnToChat();
            } catch (Exception ex) {
                logger.error("Ошибка при переходе на стартовую панель: {}", ex.getMessage(), ex);
                dialogDisplayer.showErrorDialog("Ошибка перехода: " + ex.getMessage());
            }
        });

        uploadButton.setOnAction(e -> {
            try {
                logger.debug("Нажата кнопка 'Выбрать файл'");
                var selectedFile = fileManager.selectEncryptedFileForDecrypt();
                if (selectedFile != null) {
                    sceneManager.showDecryptFinalPanel(selectedFile.getAbsolutePath());
                }
            } catch (Exception ex) {
                logger.error("Ошибка при выборе файла: {}", ex.getMessage(), ex);
                dialogDisplayer.showErrorDialog("Ошибка при выборе файла: " + ex.getMessage());
            }
        });
    }
}
