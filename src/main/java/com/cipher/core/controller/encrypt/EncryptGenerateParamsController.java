package com.cipher.core.controller.encrypt;

import com.cipher.core.dto.MandelbrotParams;
import com.cipher.core.service.MandelbrotService;
import com.cipher.core.utils.*;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.awt.image.BufferedImage;
import java.io.IOException;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class EncryptGenerateParamsController {
    private static final Logger logger = LoggerFactory.getLogger(EncryptGenerateParamsController.class);
    private final ImageUtils imageUtils;

    @FXML private ImageView imageView;
    @FXML private StackPane loadingContainer;
    @FXML private StackPane hintBoxContainer;
    @FXML private Button backButton;
    @FXML private Button regenerateButton;
    @FXML private Button manualButton;
    @FXML private Button okayButton;
    @FXML private Button swapButton;

    private final SceneManager sceneManager;
    private final TempFileManager tempFileManager;
    private final DialogDisplayer dialogDisplayer;
    private final MandelbrotService mandelbrotService;
    private final BinaryFile binaryFile;
    private Task<Image> currentTask;
    @Setter
    private String paramsFilePath;

    @FXML
    public void initialize() {
        loadHintBox();
        setupEventHandlers();
        loadInputImage();
        startImageGeneration();
    }

    private void loadHintBox() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/encrypt/hint-box-encrypt.fxml"));
            Parent hintBox = loader.load();
            hintBoxContainer.getChildren().add(hintBox);
        } catch (Exception e) {
            logger.error("Ошибка загрузки hint-box", e);
        }
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> handleBack());
        regenerateButton.setOnAction(e -> handleRegenerate());
        manualButton.setOnAction(e -> sceneManager.showManualEncryptionPanel());
        okayButton.setOnAction(e -> handleEncrypt());
        swapButton.setOnAction(e -> sceneManager.showEncryptChooseAreaPanel());
    }

    private void loadInputImage() {
        try {
            if (imageUtils.hasOriginalImage()) {
                BufferedImage originalBuffered = imageUtils.getOriginalImage();
                Image originalFx = imageUtils.convertToFxImage(originalBuffered);
                imageView.setImage(originalFx);
            }

        } catch (Exception e) {
            dialogDisplayer.showErrorDialog("Ошибка загрузки изображений");
        }
    }

    //todo переделать сохранение под ImageUtils
    private void startImageGeneration() {
        if (paramsFilePath == null) {
            logger.error("Params file path is null");
            dialogDisplayer.showErrorDialog("Ошибка: путь к параметрам не указан");
            return;
        }

        setButtonsDisabled(true);
        showLoading(true);

        currentTask = new Task<>() {
            @Override
            protected Image call() {
                try {
                    // Загрузка параметров из бинарного файла
                    MandelbrotParams params = binaryFile.loadMandelbrotParamsFromBinaryFile(paramsFilePath);
                    if (params == null) {
                        throw new IOException("Не удалось загрузить параметры из файла: " + paramsFilePath);
                    }

                    // Загрузка входного изображения для получения размеров
                    BufferedImage inputImage = imageUtils.getOriginalImage();

                    // Генерация изображения с использованием параметров
                    BufferedImage mandelbrotImage = mandelbrotService.generateImage(
                            inputImage.getWidth(),
                            inputImage.getHeight(),
                            params.zoom(),
                            params.offsetX(),
                            params.offsetY(),
                            params.maxIter()
                    );

                    return mandelbrotImage != null ? SwingFXUtils.toFXImage(mandelbrotImage, null) : null;

                } catch (Exception e) {
                    logger.error("Ошибка генерации изображения с параметрами", e);
                    return null;
                }
            }
        };

        currentTask.setOnSucceeded(e -> {
            Image resultImage = currentTask.getValue();
            if (resultImage != null) {
                ImageView resultImageView = new ImageView(resultImage);
                resultImageView.setPreserveRatio(true);
                resultImageView.setFitWidth(720);
                resultImageView.setFitHeight(540);

                // Заменяем изображение в контейнере
                if (loadingContainer.getParent() instanceof StackPane parent) {
                    parent.getChildren().remove(loadingContainer);
                    parent.getChildren().add(resultImageView);
                }

                // Сохраняем сгенерированное изображение
                saveGeneratedImage(resultImage);
            }
            setButtonsDisabled(false);
            showLoading(false);
        });

        currentTask.setOnFailed(e -> {
            logger.error("Генерация failed", currentTask.getException());
            dialogDisplayer.showErrorDialog("Ошибка генерации: " + currentTask.getException().getMessage());
            setButtonsDisabled(false);
            showLoading(false);
        });

        new Thread(currentTask).start();
    }

    //todo удалить после подключения ImageUtils
    private void saveGeneratedImage(Image resultImage) {
        try {
            BufferedImage bufferedImage = SwingFXUtils.fromFXImage(resultImage, null);
            if (bufferedImage != null) {
                tempFileManager.saveMandelbrotToTemp(bufferedImage);
            }
        } catch (Exception e) {
            logger.error("Ошибка сохранения сгенерированного изображения", e);
        }
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
        }
    }

    private void cancelCurrentTask() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
            showLoading(false);
        }
    }
}
