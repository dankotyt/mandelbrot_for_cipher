package com.cipher.core.controller.encrypt;

import com.cipher.core.service.EncryptionService;
import com.cipher.core.utils.*;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.awt.image.BufferedImage;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class EncryptManualController {
    private static final Logger logger = LoggerFactory.getLogger(EncryptManualController.class);

    @FXML private TextField zoomField;
    @FXML private TextField iterationsField;
    @FXML private TextField xField;
    @FXML private TextField yField;
    @FXML private Button saveButton;
    @FXML private Button backButton;

    private final SceneManager sceneManager;
    private final TempFileManager tempFileManager;
    private final DialogDisplayer dialogDisplayer;
    private final EncryptionService encryptionService;
    private final NumberFilter numberFilter;

    @FXML
    public void initialize() {
        setupTextFilters();
        setupEventHandlers();
    }

    private void setupTextFilters() {
        zoomField.setTextFormatter(new TextFormatter<>(numberFilter.createIntegerFilter(7)));
        iterationsField.setTextFormatter(new TextFormatter<>(numberFilter.createIntegerFilter(5)));
        xField.setTextFormatter(new TextFormatter<>(numberFilter.createDoubleFilter()));
        yField.setTextFormatter(new TextFormatter<>(numberFilter.createDoubleFilter()));
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> sceneManager.showEncryptModePanel());
        saveButton.setOnAction(e -> handleSave());
    }

    private void handleSave() {
        try {
            // Валидация полей
            if (!validateFields()) {
                return;
            }

            double zoom = Double.parseDouble(zoomField.getText());
            int iterations = Integer.parseInt(iterationsField.getText());
            double x = Double.parseDouble(xField.getText());
            double y = Double.parseDouble(yField.getText());

            // Загрузка входного изображения для получения размеров
            BufferedImage inputImage = tempFileManager.loadBufferedImageFromTemp("input.png");
            if (inputImage == null) {
                return;
            }

            // Сохранение параметров
            encryptionService.saveMandelbrotParameters(
                    inputImage.getWidth(),
                    inputImage.getHeight(),
                    zoom,
                    iterations,
                    x,
                    y
            );
            sceneManager.showEncryptGenerateParamsPanel(tempFileManager.getTempPath() + "mandelbrot_params.bin");

        } catch (NumberFormatException ex) {
            dialogDisplayer.showErrorDialog("Некорректный формат данных");
        } catch (Exception ex) {
            logger.error("Ошибка сохранения параметров", ex);
            dialogDisplayer.showErrorDialog("Ошибка сохранения параметров: " + ex.getMessage());
        }
    }

    private boolean validateFields() {
        if (zoomField.getText().isEmpty()) {
            dialogDisplayer.showErrorDialog("Введите значение масштаба");
            zoomField.requestFocus();
            return false;
        }

        if (iterationsField.getText().isEmpty()) {
            dialogDisplayer.showErrorDialog("Введите число итераций");
            iterationsField.requestFocus();
            return false;
        }

        if (xField.getText().isEmpty()) {
            dialogDisplayer.showErrorDialog("Введите смещение по оси X");
            xField.requestFocus();
            return false;
        }

        if (yField.getText().isEmpty()) {
            dialogDisplayer.showErrorDialog("Введите смещение по оси Y");
            yField.requestFocus();
            return false;
        }

        return true;
    }
}
