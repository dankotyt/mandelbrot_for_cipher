//package com.cipher.core.controller.decrypt;
//
//import com.cipher.core.utils.DialogDisplayer;
//import com.cipher.core.utils.ImageUtils;
//import com.cipher.core.utils.SceneManager;
//import com.cipher.core.utils.TempFileManager;
//import javafx.fxml.FXML;
//import javafx.scene.control.Button;
//import javafx.scene.image.Image;
//import javafx.scene.image.ImageView;
//import lombok.RequiredArgsConstructor;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.context.annotation.Scope;
//import org.springframework.stereotype.Controller;
//
//import java.awt.image.BufferedImage;
//import java.io.File;
//
//@Controller
//@Scope("prototype")
//@RequiredArgsConstructor
//public class DecryptModeController {
//    private static final Logger logger = LoggerFactory.getLogger(DecryptModeController.class);
//
//    @FXML
//    private ImageView imageView;
//    @FXML
//    private Button manualButton;
//    @FXML
//    private Button backButton;
//
//    private final SceneManager sceneManager;
//    private final TempFileManager tempFileManager;
//    private final ImageUtils imageUtils;
//    private final DialogDisplayer dialogDisplayer;
//
//    @FXML
//    public void initialize() {
//        loadInputImage();
//        setupEventHandlers();
//    }
//
//    private void loadInputImage() {
//        try {
//            if (imageUtils.hasOriginalImage()) {
//                BufferedImage originalBuffered = imageUtils.getOriginalImage();
//                Image originalFx = imageUtils.convertToFxImage(originalBuffered);
//                imageView.setImage(originalFx);
//            }
//
//        } catch (Exception e) {
//            dialogDisplayer.showErrorDialog("Ошибка загрузки изображений");
//        }
//    }
//
//    private void setupEventHandlers() {
//        backButton.setOnAction(e -> sceneManager.showDecryptBeginPanel());
//        manualButton.setOnAction(e -> handleLoadKeyFile());
//    }
//
//    private void handleLoadKeyFile() {
//        File selectedFile = tempFileManager.selectKeyFileForDecrypt();
//        if (selectedFile != null) {
//            sceneManager.showDecryptFinalPanel(selectedFile.getAbsolutePath());
//        } else {
//            dialogDisplayer.showErrorDialog("Файл-ключ не выбран!");
//        }
//    }
//}