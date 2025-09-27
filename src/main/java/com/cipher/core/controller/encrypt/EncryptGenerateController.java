package com.cipher.core.controller.encrypt;

import com.cipher.core.dto.EncryptionResult;
import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.dto.neww.EncryptionArea;
import com.cipher.core.dto.neww.EncryptionParams;
import com.cipher.core.dto.neww.SegmentationParams;
import com.cipher.core.dto.neww.SegmentationResult;
import com.cipher.core.encryption.CryptographicService;
import com.cipher.core.encryption.ImageSegmentShuffler;
import com.cipher.core.service.ImageEncryptionService;
import com.cipher.core.service.MandelbrotService;
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

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class EncryptGenerateController {
    private static final Logger logger = LoggerFactory.getLogger(EncryptGenerateController.class);
    private final CryptographicService cryptographicService;
    private final ImageSegmentShuffler imageSegmentShuffler;

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

    private final SceneManager sceneManager;
    private final ImageUtils imageUtils;
    private final DialogDisplayer dialogDisplayer;
    private final MandelbrotService mandelbrotService;
    private final ImageEncryptionService imageEncryptionService;

    private Task<Image> currentTask;
    private BufferedImage originalImage;

    @FXML
    public void initialize() {
        setupConsole();
        loadHintBox();
        loadInputImage();
        setupEventHandlers();
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
        okayButton.setOnAction(e -> handleEncrypt());
        swapButton.setOnAction(e -> sceneManager.showEncryptChooseAreaPanel());

        startImageGeneration();
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

    private void startImageGeneration() {

        setButtonsDisabled(true);
        showLoading(true);
        ConsoleManager.clear();

        currentTask = new Task<>() {
            @Override
            protected Image call() {
                try {
                    BufferedImage mandelbrotImage = mandelbrotService.generateImage();

                    return mandelbrotImage != null ? SwingFXUtils.toFXImage(mandelbrotImage, null) : null;
                } catch (Exception e) {
                    logger.error("Ошибка генерации фрактала", e);
                    ConsoleManager.log("Ошибка генерации: " + e.getMessage());
                    return null;
                }
            }
        };

        currentTask.setOnSucceeded(e -> {
            Image resultImage = currentTask.getValue();
            if (resultImage != null) {
                ImageView resultImageView = new ImageView(resultImage);
                resultImageView.setFitWidth(720);
                resultImageView.setFitHeight(540);

                if (imageContainer.getChildren().size() > 1) {
                    imageContainer.getChildren().set(1, resultImageView);
                } else {
                    imageContainer.getChildren().add(1, resultImageView);
                }
            }
            setButtonsDisabled(false);
            showLoading(false);

        });

        currentTask.setOnFailed(e -> {
            logger.error("Генерация failed", currentTask.getException());
            ConsoleManager.log("Ошибка генерации: " + currentTask.getException().getMessage());
            dialogDisplayer.showErrorDialog("Ошибка генерации");
            setButtonsDisabled(false);
            showLoading(false);
        });

        currentTask.setOnCancelled(e -> {
            ConsoleManager.log("Генерация отменена");
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
        startImageGeneration();
    }

    //todo нужно шифровать параметры через CryptograficService и передавать туда masterSeed
    private void handleEncrypt() {
        try {
            BufferedImage finalFractal = mandelbrotService
                    .generateImage(originalImage.getWidth(), originalImage.getHeight());
            SegmentationResult segmentationResult = imageSegmentShuffler.segmentAndShuffle(originalImage);

            EncryptionResult result = new EncryptionResult(
                    segmentationResult.shuffledImage(),
                    finalFractal,
                    new EncryptionParams(
                            new EncryptionArea(
                               0, 0,
                                    originalImage.getWidth(),
                                    originalImage.getHeight(),
                                    true
                            ),
                            new SegmentationParams(
                                    segmentationResult.segmentSize(),
                                    segmentationResult.paddedWidth(),
                                    segmentationResult.paddedHeight(),
                                    segmentationResult.segmentMapping()
                            ),
                            mandelbrotService.getCurrentParams()
                    )
            );
            cryptographicService.encryptData(result);
            MandelbrotParams mandelbrotParams = mandelbrotService.getCurrentParams();
            BufferedImage encryptedImage = imageEncryptionService.performEncryption(
                    previewResult.params(),
                    previewResult.originalImage()
            );

            sceneManager.showEncryptFinalPanel(encryptedImage);
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
