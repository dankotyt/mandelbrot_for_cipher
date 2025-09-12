package com.cipher.core.controller.online;

import com.cipher.client.service.impl.ClientAuthServiceImpl;
import com.cipher.common.exception.AuthException;
import com.cipher.common.exception.CryptoException;
import com.cipher.common.exception.NetworkException;
import com.cipher.core.utils.DialogDisplayer;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import lombok.Getter;

import com.cipher.core.utils.SceneManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class LoginController {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @FXML
    private BorderPane mainContainer;
    @FXML
    private GridPane wordsGrid;
    @FXML
    private Button confirmButton;
    @FXML
    private Label titleLabel;
    @FXML
    private Button backButton;

    private final SceneManager sceneManager;
    private final ClientAuthServiceImpl clientAuthService;
    private final DialogDisplayer dialogDisplayer;
    private final TextField[] wordFields = new TextField[12];

    @FXML
    public void initialize() {
        createWordFields();
    }

    private void createWordFields() {
        if (wordsGrid == null) return;

        wordsGrid.getChildren().clear();

        for (int i = 0; i < 12; i++) {
            int wordNumber = i + 1;

            Label numberLabel = new Label(wordNumber + ".");
            numberLabel.getStyleClass().add("login-number");

            TextField wordField = new TextField();
            wordField.setPromptText("Слово " + wordNumber);
            wordField.getStyleClass().add("login-field");

            wordFields[i] = wordField;

            HBox wordContainer = new HBox(5, numberLabel, wordField);
            wordContainer.getStyleClass().add("login-word-container");
            wordContainer.setAlignment(Pos.CENTER_LEFT);

            int row = i / 3;
            int col = i % 3;
            wordsGrid.add(wordContainer, col, row);
        }
    }

    @FXML
    private void handleBack() {
        sceneManager.showConnectionPanel();
    }

    @FXML
    private void handleConfirm() {
        try {
            if (clientAuthService != null) {
                List<String> words = getWordsFromFields();

                if (words.size() != 12) {
                    dialogDisplayer.showAlert("Ошибка", "Введите все 12 слов seed-фразы");
                    return;
                }

                clientAuthService.login(words);
                dialogDisplayer.showAlert("Успех", "Авторизация прошла успешно!");
                sceneManager.showAccountPanel();

            } else {
                dialogDisplayer.showAlert("Ошибка", "Сервис авторизации не доступен");
            }
        } catch (NetworkException ex) {
            logger.warn("Сетевая ошибка при авторизации", ex);
            dialogDisplayer.showErrorAlert("Сетевая ошибка", ex.getMessage());
        } catch (AuthException ex) {
            logger.warn("Ошибка авторизации", ex);
            dialogDisplayer.showErrorAlert("Ошибка авторизации", ex.getMessage());
        } catch (CryptoException ex) {
            logger.error("Криптографическая ошибка", ex);
            dialogDisplayer.showErrorAlert("Ошибка безопасности", "Криптографическая ошибка: " + ex.getMessage());
        } catch (Exception ex) {
            logger.error("Неизвестная ошибка при авторизации", ex);
            dialogDisplayer.showErrorAlert("Ошибка", "Неизвестная ошибка: " + ex.getMessage());
        }
    }

    private List<String> getWordsFromFields() {
        List<String> words = new ArrayList<>();

        for (TextField field : wordFields) {
            if (field != null) {
                String word = field.getText().trim();
                if (!word.isEmpty()) {
                    words.add(word);
                }
            }
        }

        return words;
    }
}
