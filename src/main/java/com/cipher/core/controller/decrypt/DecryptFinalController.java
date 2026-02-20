package com.cipher.core.controller.decrypt;

import com.cipher.core.encryption.ImageDecrypt;
import com.cipher.core.service.network.CryptoKeyManager;
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
    private final CryptoKeyManager cryptoKeyManager;

    private String keyFilePath;
    private InetAddress peerAddress;

    public void setKeyFilePath(String keyFilePath) {
        this.keyFilePath = keyFilePath;
        File file = new File(keyFilePath);

        this.peerAddress = cryptoKeyManager.getConnectedPeer();
        if (this.peerAddress == null) {
            dialogDisplayer.showErrorDialog("Не установлено соединение с пиром. Сначала подключитесь к устройству.");
            return;
        }

        startDecryption(file);
    }

    @FXML
    public void initialize() {
        setupEventHandlers();

        this.peerAddress = cryptoKeyManager.getConnectedPeer();
        if (this.peerAddress != null) {
            logger.info("✅ Контроллер дешифрования инициализирован с пиром: {}",
                    peerAddress.getHostAddress());
        } else {
            logger.warn("⚠️ Контроллер дешифрования инициализирован без подключенного пира");
        }
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> sceneManager.showDecryptBeginPanel());
        saveButton.setOnAction(e -> {
            BufferedImage decryptedImage = tempFileManager.loadBufferedImageFromTemp("decrypted_image.png");
            if (decryptedImage != null) {
                tempFileManager.saveDecryptedImage(decryptedImage);
            } else {
                dialogDisplayer.showErrorDialog("Нет расшифрованного изображения для сохранения");
            }
        });
    }

    private void startDecryption(File file) {
        if (keyFilePath == null || keyFilePath.isEmpty()) {
            dialogDisplayer.showErrorDialog("Путь к файлу-ключу не указан");
            return;
        }

        if (peerAddress == null) {
            peerAddress = cryptoKeyManager.getConnectedPeer();
            if (peerAddress == null) {
                dialogDisplayer.showErrorDialog("Не установлено соединение с пиром. Сначала подключитесь к устройству.");
                return;
            }
            logger.info("🔄 Пир восстановлен из ConnectionManager: {}", peerAddress.getHostAddress());
        }

        Task<BufferedImage> decryptTask = getBufferedImageTask(file);

        new Thread(decryptTask).start();
    }

    private Task<BufferedImage> getBufferedImageTask(File file) {
        Task<BufferedImage> decryptTask = new Task<>() {
            @Override
            protected BufferedImage call() throws Exception {
                return imageDecrypt.decryptImage(file);
            }
        };

        decryptTask.setOnSucceeded(e -> {
            try {
                displayDecryptedImage();
                dialogDisplayer.showSuccessDialog("Изображение успешно расшифровано!");
            } catch (Exception ex) {
                logger.error("Ошибка при загрузке расшифрованного изображения", ex);
            }
        });

        decryptTask.setOnFailed(e -> {
            logger.error("Ошибка при расшифровке изображения", decryptTask.getException());
            dialogDisplayer.showErrorDialog("Ошибка при расшифровке: " + decryptTask.getException().getMessage());
        });
        return decryptTask;
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
            dialogDisplayer.showErrorDialog("Не удалось загрузить расшифрованное изображение");
        }
    }
}
