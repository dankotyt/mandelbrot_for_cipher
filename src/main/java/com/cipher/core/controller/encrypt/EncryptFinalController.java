package com.cipher.core.controller.encrypt;

import com.cipher.core.dto.neww.EncryptionDataResult;
import com.cipher.core.dto.neww.EncryptionParams;
import com.cipher.core.dto.neww.EncryptionPreviewResult;
import com.cipher.core.service.ImageEncryptionService;
import com.cipher.core.utils.EncryptionParamsSerializer;
import com.cipher.core.utils.SceneManager;
import com.cipher.core.utils.TempFileManager;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import lombok.RequiredArgsConstructor;
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
    private final ImageEncryptionService encryptionService;
    private final EncryptionParamsSerializer paramsSerializer;

    private EncryptionPreviewResult previewResult;
    private BufferedImage encryptedImage;
    private EncryptionParams encryptionParams;

    public void setPreviewResult(EncryptionPreviewResult previewResult) {
        try {
            this.encryptionParams = previewResult.params();
            this.encryptedImage = encryptionService.performEncryption(
                    previewResult.params(),
                    previewResult.originalImage()
            );
            displayEncryptedImage();
        } catch (Exception e) {
            logger.error("Ошибка шифрования", e);
        }
    }

    public void setEncryptedImage(BufferedImage encryptedImage) {
        this.encryptedImage = encryptedImage;

        // Загружаем параметры из временного файла
        this.encryptionParams = tempFileManager.loadEncryptionParams();

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

    private void performEncryption() {
        if (previewResult == null) {
            logger.error("Параметры шифрования не установлены");
            return;
        }

        try {
            // Выполняем финальное шифрование
            encryptedImage = encryptionService.performEncryption(
                    previewResult.params(),
                    previewResult.originalImage()
            );

            displayEncryptedImage();

        } catch (Exception e) {
            logger.error("Ошибка шифрования", e);
        }
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
        if (encryptedImage != null && previewResult != null) {
            try {
                // Сериализуем и шифруем параметры
                EncryptionDataResult encryptedParams = tempFileManager.loadEncryptedParamsFromTemp();

                // Сохраняем через проводник
                tempFileManager.saveEncryptedData(encryptedImage, encryptedParams);

                // Возвращаемся на начальный экран
                sceneManager.showStartPanel();

            } catch (Exception e) {
                logger.error("Ошибка сохранения", e);
            }
        }
    }
}
