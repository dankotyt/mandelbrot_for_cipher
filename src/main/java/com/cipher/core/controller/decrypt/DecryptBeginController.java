package com.cipher.core.controller.decrypt;

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
public class DecryptBeginController {
    private static final Logger logger = LoggerFactory.getLogger(DecryptBeginController.class);

    @FXML private Button uploadButton;
    @FXML private Button backButton;

    private final SceneManager sceneManager;
    private final TempFileManager tempFileManager;
    private final DialogDisplayer dialogDisplayer;

    @FXML
    public void initialize() {
        setupEventHandlers();
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> {
            try {
                logger.debug("Нажата кнопка 'Назад'");
                sceneManager.showChatPanel();
            } catch (Exception ex) {
                logger.error("Ошибка при переходе на стартовую панель: {}", ex.getMessage(), ex);
                dialogDisplayer.showErrorDialog("Ошибка перехода: " + ex.getMessage());
            }
        });

        uploadButton.setOnAction(e -> {
            try {
                logger.debug("Нажата кнопка 'Выбрать файл'");
                var selectedFile = tempFileManager.selectEncryptedFileForDecrypt();
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
