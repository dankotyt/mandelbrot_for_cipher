package com.cipher.core.controller.online;

import com.cipher.client.service.impl.SeedServiceImpl;
import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class SeedGenerationPanelController {
    private static final Logger logger = LoggerFactory.getLogger(SeedGenerationPanelController.class);

    @FXML private GridPane wordsGrid;
    @FXML private Label warningLabel;
    @FXML private Button confirmButton;
    @FXML private Button backButton;

    private final SceneManager sceneManager;
    private final SeedServiceImpl seedService;
    private final DialogDisplayer dialogDisplayer;
    private String seedPhrase;

    public void setSeedPhrase(String seedPhrase) {
        this.seedPhrase = seedPhrase;
        if (wordsGrid != null) {
            displaySeedPhrase();
        }
    }

    @FXML
    public void initialize() {
        setupUI();
        generateSeedPhrase();
    }

    private void setupUI() {
        warningLabel.setText("ЗАПИШИТЕ эти слова в безопасном месте!\nЭто единственный способ восстановить доступ к аккаунту.");
        warningLabel.setTextAlignment(TextAlignment.CENTER);

        backButton.setOnAction(e -> sceneManager.showConnectionPanel());
        confirmButton.setOnAction(e -> handleConfirm());
    }

    private void generateSeedPhrase() {
        try {
            seedPhrase = seedService.generateAccount();
            displaySeedPhrase();
        } catch (Exception e) {
            dialogDisplayer.showErrorAlert("Ошибка", "Не удалось сгенерировать seed-фразу");
            sceneManager.showConnectionPanel();
        }
    }

    private void displaySeedPhrase() {
        if (seedPhrase == null ) return;

        wordsGrid.getChildren().clear();
        String[] words = seedPhrase.split(" ");

        for (int i = 0; i < words.length; i++) {
            int wordNumber = i + 1;

            Label numberLabel = new Label(wordNumber + ".");
            numberLabel.getStyleClass().add("seed-number");

            Label wordLabel = new Label(words[i]);
            wordLabel.getStyleClass().add("seed-word");

            HBox wordContainer = new HBox(15, numberLabel, wordLabel);
            wordContainer.setAlignment(Pos.CENTER_LEFT);

            int row = i / 3;
            int col = i % 3;
            wordsGrid.add(wordContainer, col, row);
        }
    }

    @FXML
    private void handleConfirm() {
        try {
            dialogDisplayer.showAlert("Успех", "Аккаунт успешно создан!");
            sceneManager.showLoginPanel();
        } catch (Exception e) {
            logger.error("Ошибка при подтверждении seed-фразы", e);
            dialogDisplayer.showErrorAlert("Ошибка", "Не удалось завершить регистрацию: " + e.getMessage());
        }
    }
}
