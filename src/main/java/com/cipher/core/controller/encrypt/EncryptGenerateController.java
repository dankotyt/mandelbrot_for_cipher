package com.cipher.core.controller.encrypt;

import com.cipher.core.service.MandelbrotService;
import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import com.cipher.core.utils.TempFileManager;
import com.cipher.view.javafx.JavaFXImpl;
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

import java.awt.image.BufferedImage;

@RequiredArgsConstructor
public class EncryptGenerateController {
    private static final Logger logger = LoggerFactory.getLogger(EncryptGenerateController.class);

    @FXML
    private ImageView imageView;
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
    private final TempFileManager tempFileManager;
    private final DialogDisplayer dialogDisplayer;
    private final MandelbrotService mandelbrotService;
    private Task<Image> currentTask;

    @FXML
    public void initialize() {
        setupConsole();
        loadHintBox();
        setupEventHandlers();
        loadInputImage();
        startImageGeneration();
    }

    private void setupConsole() {
        console.getStyleClass().add("generate-console");
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> handleBack());
        regenerateButton.setOnAction(e -> handleRegenerate());
        manualButton.setOnAction(e -> sceneManager.showManualEncryptionPanel());
        okayButton.setOnAction(e -> handleEncrypt());
        swapButton.setOnAction(e -> sceneManager.showEncryptChooseAreaPanel());
    }

    private void loadHintBox() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/components/hint-box.fxml"));
            Parent hintBox = loader.load();
            hintBoxContainer.getChildren().add(hintBox);
        } catch (Exception e) {
            logger.error("Ошибка загрузки hint-box", e);
        }
    }

    private void loadInputImage() {
        ImageView inputImageView = tempFileManager.loadInputImageFromTemp();
        if (inputImageView != null) {
            imageView.setImage(inputImageView.getImage());
        }
    }

    private void startImageGeneration() {
        setButtonsDisabled(true);
        showLoading(true);

        currentTask = new Task<>() {
            @Override
            protected Image call() {
                try {
                    BufferedImage mandelbrotImage = mandelbrotService.generateImage();
                    return mandelbrotImage != null ? SwingFXUtils.toFXImage(mandelbrotImage, null) : null;
                } catch (Exception e) {
                    logger.error("Ошибка генерации изображения", e);
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
                resultImageView.setPreserveRatio(true);

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
            dialogDisplayer.showErrorDialog("Ошибка генерации");
            setButtonsDisabled(false);
            showLoading(false);
        });

        currentTask.setOnCancelled(e -> {
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

    private void handleEncrypt() {
        try {
            BufferedImage imageToEncrypt = tempFileManager.loadBufferedImageFromTemp("input.png");
            if (imageToEncrypt != null) {
                sceneManager.showEncryptFinalPanel(imageToEncrypt);
            }
        } catch (Exception e) {
            logger.error("Ошибка загрузки изображения", e);
            dialogDisplayer.showErrorDialog("Ошибка загрузки изображения");
        }
    }

    private void cancelCurrentTask() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
            showLoading(false);
        }
    }
}
