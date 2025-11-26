package com.cipher.core.controller.decrypt;

//import com.cipher.core.encryption.ImageDecrypt;
import com.cipher.core.encryption.ImageDecrypt;
import com.cipher.core.service.network.ConnectionManager;
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

import java.awt.image.BufferedImage;
import java.io.File;
import java.net.InetAddress;

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
    private final ConnectionManager connectionManager;

    private String keyFilePath;
    private InetAddress peerAddress;

    public void setKeyFilePath(String keyFilePath) {
        this.keyFilePath = keyFilePath;
        File file = new File(keyFilePath);

        this.peerAddress = connectionManager.getConnectedPeer();
        if (this.peerAddress == null) {
            dialogDisplayer.showErrorDialog("Не установлено соединение с пиром. Сначала подключитесь к устройству.");
            return;
        }

        startDecryption(file);
    }

    @FXML
    public void initialize() {
        setupEventHandlers();

        this.peerAddress = connectionManager.getConnectedPeer();
        if (this.peerAddress != null) {
            logger.info("✅ Контроллер дешифрования инициализирован с пиром: {}",
                    peerAddress.getHostAddress());
        } else {
            logger.warn("⚠️ Контроллер дешифрования инициализирован без подключенного пира");
        }
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> sceneManager.showDecryptModePanel());
        saveButton.setOnAction(e -> tempFileManager.saveDecryptedImage()); //todo заменить на полное сохранение
    }

    private void startDecryption(File file) {
        if (keyFilePath == null || keyFilePath.isEmpty()) {
            dialogDisplayer.showErrorDialog("Путь к файлу-ключу не указан");
            return;
        }

        if (peerAddress == null) {
            peerAddress = connectionManager.getConnectedPeer();
            if (peerAddress == null) {
                dialogDisplayer.showErrorDialog("Не установлено соединение с пиром. Сначала подключитесь к устройству.");
                return;
            }
            logger.info("🔄 Пир восстановлен из ConnectionManager: {}", peerAddress.getHostAddress());
        }

        Task<BufferedImage> decryptImageTask = new Task<>() {
            @Override
            protected BufferedImage call() throws Exception {
                return imageDecrypt.decryptImage(file, peerAddress);

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
