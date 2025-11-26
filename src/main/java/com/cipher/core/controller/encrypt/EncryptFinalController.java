package com.cipher.core.controller.encrypt;

import com.cipher.client.service.chat.ChatService;
import com.cipher.core.dto.encryption.EncryptionDataResult;
import com.cipher.core.utils.SceneManager;
import com.cipher.core.utils.TempFileManager;
import com.cipher.core.utils.DialogDisplayer;
import javafx.embed.swing.SwingFXUtils;
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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class EncryptFinalController {
    private static final Logger logger = LoggerFactory.getLogger(EncryptFinalController.class);

    @FXML private ImageView imageView;
    @FXML private Button sendToChatButton;
    @FXML private Button backButton;

    private final SceneManager sceneManager;
    private final TempFileManager tempFileManager;
    private final ChatService chatService;
    private final DialogDisplayer dialogDisplayer;

    private BufferedImage encryptedImage;
    @Setter
    private EncryptionDataResult encryptionDataResult;

    public void setEncryptedImage(BufferedImage encryptedImage) {
        this.encryptedImage = encryptedImage;
        displayEncryptedImage();
    }

    @FXML
    public void initialize() {
        setupEventHandlers();
        updateSendButtonState();
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> handleBack());
        sendToChatButton.setOnAction(e -> handleSendToChat());
    }

    private void displayEncryptedImage() {
        if (encryptedImage != null) {
            Image fxImage = SwingFXUtils.toFXImage(encryptedImage, null);
            imageView.setImage(fxImage);
        }
    }

    private void updateSendButtonState() {
        boolean isConnected = chatService.isConnected();
        sendToChatButton.setDisable(!isConnected);

        if (!isConnected) {
            sendToChatButton.setText("Нет подключения");
        } else {
            sendToChatButton.setText("Отправить в чат");
        }
    }

    private void handleBack() {
        sceneManager.showEncryptChooseAreaPanel();
    }

    private void handleSendToChat() {
        if (encryptedImage != null && encryptionDataResult != null) {
            try {
                // Проверяем соединение
                if (!chatService.isConnected()) {
                    dialogDisplayer.showErrorDialog(
                            "Сначала установите соединение с пиром");
                    return;
                }

                // Конвертируем BufferedImage в byte[] для отправки
                byte[] imageData = convertImageToBytes(encryptedImage);

                // Отправляем изображение в чат
                chatService.sendImage(imageData, "encrypted_image.png");

                // Сохраняем параметры шифрования во временные файлы (на всякий случай)
                tempFileManager.saveEncryptedData(encryptedImage, encryptionDataResult);

                // Переходим в чат
                sceneManager.showChatPanel();

                dialogDisplayer.showSuccessDialog(
                        "Зашифрованное изображение отправлено в чат!");

            } catch (Exception e) {
                logger.error("Ошибка отправки в чат", e);
                dialogDisplayer.showErrorDialog(
                        "Не удалось отправить изображение в чат: " + e.getMessage());
            }
        } else {
            dialogDisplayer.showErrorDialog(
                    "Нет данных для отправки");
        }
    }

    private byte[] convertImageToBytes(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        }
    }
}