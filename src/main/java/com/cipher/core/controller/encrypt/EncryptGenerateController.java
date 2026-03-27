package com.cipher.core.controller.encrypt;

import com.cipher.core.service.encryption.ImageEncrypt;
import com.cipher.core.service.encryption.MandelbrotService;
import com.cipher.core.service.network.CryptoKeyManager;
import com.cipher.core.utils.*;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.awt.image.BufferedImage;
import java.net.InetAddress;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class EncryptGenerateController {
    private static final Logger logger = LoggerFactory.getLogger(EncryptGenerateController.class);

    @FXML private ImageView imageView;
    @FXML private StackPane loadingContainer;
    @FXML private StackPane hintBoxContainer;
    @FXML private StackPane imageContainer;
    @FXML private TextArea console;
    @FXML private Button regenerateButton;
    @FXML private Button manualButton;
    @FXML private Button okayButton;
    @FXML private Button swapButton;
    @FXML private Button backButton;

    private final ImageEncrypt imageEncrypt;
    private final SceneManager sceneManager;
    private final ImageUtils imageUtils;
    private final DialogDisplayer dialogDisplayer;
    private final MandelbrotService mandelbrotService;
    private final CryptoKeyManager cryptoKeyManager;

    private Task<Image> currentTask;
    private BufferedImage originalImage;

    @FXML
    public void initialize() {
        setupConsole();
        loadHintBox();
        loadInputImage();
        setupEventHandlers();
        try {
            InetAddress peer = cryptoKeyManager.getConnectedPeer();
            byte[] sharedSecret = cryptoKeyManager.getMasterSeedFromDH(InetAddress.getByName(peer.getHostAddress()));

            imageEncrypt.prepareSession(sharedSecret);

            mandelbrotService.setTargetWidth(originalImage.getWidth());
            mandelbrotService.setTargetHeight(originalImage.getHeight());

            generateUntilGood();
        } catch (Exception e) {
            logger.error("Ошибка подготовки сессии", e);
            dialogDisplayer.showErrorDialog("Ошибка подготовки сессии");
        }
    }

    private void setupConsole() {
        console.getStyleClass().add("generate-console");
        ConsoleManager.setConsole(console);
        ConsoleManager.clear();
        ConsoleManager.log("Генерация изображения...");
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> handleBack());
        regenerateButton.setOnAction(e -> handleRegenerate());
        manualButton.setOnAction(e -> sceneManager.showManualEncryptionPanel());
        okayButton.setOnAction(e -> handleEncryptWholeImage());
        swapButton.setOnAction(e -> sceneManager.showEncryptChooseAreaPanel());
    }

    private void loadHintBox() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/encrypt/hint-box.fxml"));
            Parent hintBox = loader.load();
            hintBoxContainer.getChildren().add(hintBox);
        } catch (Exception e) {
            logger.error("Ошибка загрузки hint-box", e);
        }
    }

    private void loadInputImage() {
        try {
            if (imageUtils.hasOriginalImage()) {
                originalImage = imageUtils.getOriginalImage();
                Image originalFx = imageUtils.convertToFxImage(originalImage);
                imageView.setImage(originalFx);
            }

        } catch (Exception e) {
            dialogDisplayer.showErrorDialog("Ошибка загрузки изображений");
        }
    }

    private void generateUntilGood() {
        setButtonsDisabled(true);
        showLoading(true);
        ConsoleManager.clear();

        currentTask = new Task<Image>() {
            @Override
            protected Image call() throws Exception {
                int maxAttempts = 50;
                for (int i = 0; i < maxAttempts; i++) {
                    if (isCancelled()) return null;

                    BufferedImage fractal = imageEncrypt.generateNextFractal(
                            originalImage.getWidth(), originalImage.getHeight());
                    if (!mandelbrotService.isFractalValid(fractal)) {
                        ConsoleManager.log("Попытка " + (i+1) + ": фрактал не подходит, продолжаем...");
                        continue;
                    }
                    return SwingFXUtils.toFXImage(fractal, null);
                }
                throw new RuntimeException("Не удалось получить качественный фрактал");
            }
        };

        currentTask.setOnSucceeded(e -> {
            Image result = currentTask.getValue();
            if (result != null) {
                ImageView iv = new ImageView(result);
                iv.setFitWidth(720); iv.setFitHeight(540);
                imageContainer.getChildren().set(1, iv);
            }
            setButtonsDisabled(false);
            showLoading(false);
        });

        currentTask.setOnFailed(e -> {
            logger.error("Ошибка генерации", currentTask.getException());
            ConsoleManager.log("Ошибка: " + currentTask.getException().getMessage());
            setButtonsDisabled(false);
            showLoading(false);
        });

        new Thread(currentTask).start();
    }

    private void showLoading(boolean show) {
        loadingContainer.setVisible(show);
        loadingContainer.setManaged(show);
    }

    private void setButtonsDisabled(boolean disabled) {
        regenerateButton.setDisable(disabled);
        manualButton.setDisable(disabled);
        okayButton.setDisable(disabled);
        swapButton.setDisable(disabled);
    }

    private void handleBack() {
        cancelCurrentTask();
        sceneManager.showEncryptModePanel();
    }

    private void handleRegenerate() {
        cancelCurrentTask();
        try {
            InetAddress peer = cryptoKeyManager.getConnectedPeer();
            byte[] sharedSecret = cryptoKeyManager.getMasterSeedFromDH(InetAddress.getByName(peer.getHostAddress()));
            imageEncrypt.prepareSession(sharedSecret); // новая соль, сброс attemptCount
            generateUntilGood();
        } catch (Exception e) {
            logger.error("Ошибка при регенерации", e);
            dialogDisplayer.showErrorDialog("Ошибка подготовки фрактала");
        }
    }

    private void handleEncryptWholeImage() {
        try {
            if (!imageUtils.hasOriginalImage()) {
                logger.error("original image is null");
            }
            imageEncrypt.encryptWhole(originalImage);
        } catch (Exception e) {
            logger.error("Ошибка шифрования", e);
            dialogDisplayer.showErrorDialog("Ошибка шифрования: " + e.getMessage());
        }
    }

    private void cancelCurrentTask() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
            showLoading(false);
        }
    }
}
