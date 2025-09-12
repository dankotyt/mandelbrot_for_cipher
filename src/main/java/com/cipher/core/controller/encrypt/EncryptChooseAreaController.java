package com.cipher.core.controller.encrypt;

import com.cipher.core.encryption.ImageEncrypt;
import com.cipher.core.utils.CoordinateUtils;
import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import com.cipher.core.utils.TempFileManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class EncryptChooseAreaController {

    private static final Logger logger = LoggerFactory.getLogger(EncryptChooseAreaController.class);

    @FXML private ImageView imageView;
    @FXML private ImageView mandelbrotImageView;
    @FXML private Canvas canvas;
    @FXML private StackPane imageContainer;
    @FXML private StackPane hintBoxContainer;
    @FXML private Button backButton;
    @FXML private Button encryptWholeButton;
    @FXML private Button encryptPartButton;
    @FXML private Button resetPartButton;
    @FXML private Button swapButton;

    private final SceneManager sceneManager;
    private final TempFileManager tempFileManager;
    private final DialogDisplayer dialogDisplayer;
    private final ImageEncrypt imageEncrypt;

    private Point2D startPoint;
    private Point2D endPoint;
    private boolean drawingRectangle = false;
    private boolean rectangleSelected = false;
    private final List<Rectangle2D> rectangles = new ArrayList<>();
    private CoordinateUtils coordUtils;

    @FXML
    public void initialize() {
        setupCanvas();
        loadHintBox();
        setupEventHandlers();
        loadImages();
    }

    private void setupCanvas() {
        coordUtils = new CoordinateUtils(canvas);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseReleased(this::handleMouseReleased);
        canvas.setOnMouseDragged(this::handleMouseDragged);

        canvas.setFocusTraversable(true);
        canvas.requestFocus();
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> handleBack());
        encryptWholeButton.setOnAction(e -> handleEncryptWhole());
        encryptPartButton.setOnAction(e -> handleEncryptPart());
        resetPartButton.setOnAction(e -> handleResetPart());
        swapButton.setOnAction(e -> handleSwap());
    }

    private void loadHintBox() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/components/hint-box-encrypt-part.fxml"));
            Parent hintBox = loader.load();
            hintBoxContainer.getChildren().add(hintBox);
        } catch (Exception e) {
            logger.error("Ошибка загрузки hint-box", e);
        }
    }

    private void loadImages() {
        try {
            // Используем методы из TempFileManager
            ImageView inputImageView = tempFileManager.loadInputImageFromTemp();
            if (inputImageView != null && inputImageView.getImage() != null) {
                imageView.setImage(inputImageView.getImage());
            }

            // Загрузка изображения Мандельброта
            Image mandelbrotImage = tempFileManager.loadImageFromTemp("mandelbrot.png");
            if (mandelbrotImage != null) {
                mandelbrotImageView.setImage(mandelbrotImage);
                //mandelbrotImageView.setPreserveRatio(true);
            }

        } catch (Exception e) {
            logger.error("Ошибка загрузки изображений", e);
            dialogDisplayer.showErrorDialog("Ошибка загрузки изображений");
        }
    }

    private void handleMousePressed(MouseEvent e) {
        if (e.isPrimaryButtonDown()) {
            startPoint = new Point2D(e.getX(), e.getY());
            drawingRectangle = true;
        } else if (e.isSecondaryButtonDown()) {
            clearRectangles();
        }
    }

    private void handleMouseReleased(MouseEvent e) {
        if (e.getButton() == MouseButton.PRIMARY && !rectangleSelected) {
            endPoint = new Point2D(e.getX(), e.getY());
            drawingRectangle = false;

            if (startPoint != null && !startPoint.equals(endPoint)) {
                double x = Math.min(startPoint.getX(), endPoint.getX());
                double y = Math.min(startPoint.getY(), endPoint.getY());
                double width = Math.abs(startPoint.getX() - endPoint.getX());
                double height = Math.abs(startPoint.getY() - endPoint.getY());

                Image image = imageView.getImage();
                if (image != null) {
                    Rectangle2D imageRect = coordUtils.convertCanvasToImageCoords(
                            x, y, width, height, image.getWidth(), image.getHeight());

                    // Проверяем, что прямоугольник внутри изображения
                    if (imageRect.getMinX() >= 0 && imageRect.getMinY() >= 0 &&
                            imageRect.getMaxX() <= image.getWidth() &&
                            imageRect.getMaxY() <= image.getHeight()) {

                        rectangles.add(imageRect);
                        rectangleSelected = true;
                        drawRectangles();
                    } else {
                        dialogDisplayer.showErrorMessage("Выделенная область выходит за границы изображения!");
                    }
                }
            }
        }
    }

    private void handleMouseDragged(MouseEvent e) {
        if (e.isPrimaryButtonDown() && !rectangleSelected) {
            endPoint = new Point2D(e.getX(), e.getY());
            drawSelectionRectangle();
        }
    }

    private void drawSelectionRectangle() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (drawingRectangle && startPoint != null && endPoint != null) {
            double x = Math.min(startPoint.getX(), endPoint.getX());
            double y = Math.min(startPoint.getY(), endPoint.getY());
            double width = Math.abs(startPoint.getX() - endPoint.getX());
            double height = Math.abs(startPoint.getY() - endPoint.getY());

            gc.setStroke(Color.web("#eb3471"));
            gc.setLineWidth(2);
            gc.strokeRect(x, y, width, height);
        }
    }

    private void drawRectangles() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (rectangles.isEmpty()) {
            return;
        }

        Image image = imageView.getImage();
        if (image == null) {
            return;
        }

        double scaleX = canvas.getWidth() / image.getWidth();
        double scaleY = canvas.getHeight() / image.getHeight();

        for (Rectangle2D rect : rectangles) {
            double x = rect.getMinX() * scaleX;
            double y = rect.getMinY() * scaleY;
            double width = rect.getWidth() * scaleX;
            double height = rect.getHeight() * scaleY;

            gc.setStroke(Color.web("#eb3471"));
            gc.setLineWidth(2);
            gc.strokeRect(x, y, width, height);

            // Добавляем полупрозрачную заливку
            gc.setFill(Color.web("#eb3471", 0.2));
            gc.fillRect(x, y, width, height);
        }
    }

    private void clearRectangles() {
        rectangles.clear();
        rectangleSelected = false;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    private boolean hasRectangle() {
        return !rectangles.isEmpty();
    }

    private Rectangle2D getSelectedRectangle() {
        return rectangles.isEmpty() ? null : rectangles.getFirst();
    }

    private void handleBack() {
        clearRectangles();
        sceneManager.showEncryptModePanel();
    }

    private void handleEncryptWhole() {
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

    private void handleEncryptPart() {
        if (!hasRectangle()) {
            dialogDisplayer.showErrorMessage("Необходимо выделить зону шифрования!");
            return;
        }

        try {
            BufferedImage imageToEncrypt = tempFileManager.loadBufferedImageFromTemp("input.png");
            if (imageToEncrypt == null) {
                dialogDisplayer.showErrorMessage("Не удалось загрузить изображение для шифрования");
                return;
            }

            Rectangle2D selectedRectangle = getSelectedRectangle();
            if (selectedRectangle == null) {
                dialogDisplayer.showErrorMessage("Не удалось получить выделенную область");
                return;
            }

            BufferedImage encryptedImage = imageEncrypt.encryptSelectedArea(
                    imageToEncrypt, selectedRectangle);

            if (encryptedImage != null) {
                sceneManager.showEncryptFinalPanel(encryptedImage);
                clearRectangles();
            } else {
                dialogDisplayer.showErrorMessage("Ошибка при шифровании области");
            }

        } catch (Exception e) {
            logger.error("Ошибка шифрования области", e);
            dialogDisplayer.showErrorDialog("Ошибка шифрования: " + e.getMessage());
        }
    }

    private void handleResetPart() {
        clearRectangles();
    }

    private void handleSwap() {
        clearRectangles();
        sceneManager.showChoosenMandelbrotPanel();
    }
}
