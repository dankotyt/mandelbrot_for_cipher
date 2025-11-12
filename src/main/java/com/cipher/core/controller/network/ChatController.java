package com.cipher.core.controller.network;

import com.cipher.client.service.chat.ChatService;
import com.cipher.common.dto.chat.ChatMessageDTO;
import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class ChatController {
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @FXML private Button backButton;
    @FXML private Button sendButton;
    @FXML private Button fileButton;
    @FXML private Button imageButton;
    @FXML private Button clearChatButton;
    @FXML private Button encryptionInfoButton;
    @FXML private TextArea messageTextArea;
    @FXML private VBox messagesContainer;
    @FXML private ScrollPane messagesScrollPane;
    @FXML private StackPane noMessagesPane;
    @FXML private Label statusLabel;
    @FXML private Label encryptionStatusLabel;
    @FXML private Label chatTitleLabel;
    @FXML private Label connectionInfoLabel;

    private final SceneManager sceneManager;
    private final DialogDisplayer dialogDisplayer;
    private final ChatService chatService;

    private String remoteDeviceName;
    private String remoteDeviceIp;

    @FXML
    public void initialize() {
        try {
            logger.info("💬 Инициализация ChatController");

            setupEventHandlers();
            setupUI();

            logger.info("✅ ChatController инициализирован");

        } catch (Exception e) {
            logger.error("❌ Ошибка инициализации ChatController: {}", e.getMessage(), e);
            throw e;
        }
    }

    @FXML
    private void handleClearChat() {
        if (messagesContainer.getChildren().isEmpty()) {
            dialogDisplayer.showAlert("Информация", "История чата уже пуста");
            return;
        }

        boolean confirmed = dialogDisplayer.showConfirmationDialog(
                "🧹 Очистка чата",
                "Вы уверены, что хотите очистить историю сообщений?",
                "Это действие нельзя отменить. Все сообщения будут удалены.",
                "Очистить",
                "Отмена"
        );

        if (confirmed) {
            messagesContainer.getChildren().clear();
            showNoMessagesHint();
            logger.info("🗑️ История чата очищена");
        }
    }

    /**
     * Настройка UI-слушателей вместо имплементации бизнес-интерфейса
     */
    private void setupChatListener() {
        ChatService.ChatListener chatAdapter = new ChatService.ChatListener() {
            @Override
            public void onMessageReceived(ChatMessageDTO message) {
                Platform.runLater(() -> {
                    displayTextMessage(message.getSender(), message.getContent(),
                            message.getTimestamp(), false);
                    updateStatus("💬 Новое сообщение");
                });
            }

            @Override
            public void onImageReceived(ChatMessageDTO imageMessage) {
                Platform.runLater(() -> {
                    displayImageMessage(imageMessage.getFileData(), imageMessage.getFileName(), false);
                    updateStatus("🖼️ Получено изображение");

                    // Показываем диалог сохранения
                    showFileSaveDialog(imageMessage.getFileName(), imageMessage.getFileData(), "изображение");
                });
            }

            @Override
            public void onFileReceived(ChatMessageDTO fileMessage) {
                Platform.runLater(() -> {
                    displayFileMessage(fileMessage.getFileName(), fileMessage.getFileSize(), false);
                    updateStatus("📎 Получен файл");
                });
            }

            @Override
            public void onConnectionStatusChanged(boolean connected, String peerInfo) {
                Platform.runLater(() -> {
                    String status = connected ? "✅ " + peerInfo : "🔌 " + peerInfo;
                    updateStatus(status);

                    if (connected) {
                        encryptionStatusLabel.setText("🔒 Шифрование активно");
                    } else {
                        encryptionStatusLabel.setText("⚠️ Шифрование неактивно");
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                Platform.runLater(() -> {
                    dialogDisplayer.showErrorDialog(errorMessage);
                    updateStatus("❌ " + errorMessage);
                });
            }

            @Override
            public void onIncomingConnection(String peerIp) {
                logger.info("🔗 Входящее подключение от: {}", peerIp);
                // В новой архитекции это обрабатывается автоматически
            }
        };

        // Регистрируем адаптер в сервисе
        chatService.addListener(chatAdapter);
    }

    /**
     * Устанавливает информацию о подключении к пиру
     */
    public void setConnectionInfo(String deviceName, String deviceIp) {
        this.remoteDeviceName = deviceName;
        this.remoteDeviceIp = deviceIp;

        Platform.runLater(() -> {
            chatTitleLabel.setText("💬 Чат с " + deviceName);
            connectionInfoLabel.setText("🌐 IP: " + deviceIp);
            updateStatus("Подключение...");

            // ✅ УБРАЛИ порт - используем новый метод
            boolean connected = chatService.connectToPeer(deviceIp);

            if (connected) {
                updateStatus("✅ Подключение установлено");
            } else {
                updateStatus("❌ Ошибка подключения");
                dialogDisplayer.showErrorDialog("Не удалось активировать чат с " + deviceIp);
            }
        });
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> handleBack());
        sendButton.setOnAction(e -> handleSendMessage());
        fileButton.setOnAction(e -> handleFileSend());
        imageButton.setOnAction(e -> handleImageSend());
        clearChatButton.setOnAction(e -> handleClearChat());
        encryptionInfoButton.setOnAction(e -> showEncryptionInfo());

        messageTextArea.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER:
                    if (event.isShiftDown()) {
                        messageTextArea.appendText("\n");
                    } else {
                        event.consume();
                        handleSendMessage();
                    }
                    break;
            }
        });
    }

    private void setupUI() {
        messageTextArea.setWrapText(true);
        messagesScrollPane.vvalueProperty().bind(messagesContainer.heightProperty());
        showNoMessagesHint();
    }

    @FXML
    private void handleSendMessage() {
        String messageText = messageTextArea.getText().trim();

        if (messageText.isEmpty()) {
            return;
        }

        if (!chatService.isConnected()) {
            dialogDisplayer.showTimedErrorAlert("Ошибка", "Соединение не установлено", 3);
            updateStatus("❌ Соединение потеряно");
            return;
        }

        try {
            // Отправляем сообщение
            chatService.sendMessage(messageText);

            // Отображаем локально
            displayTextMessage("Вы", messageText, LocalDateTime.now(), true);

            messageTextArea.clear();
            updateStatus("✅ Сообщение отправлено");

        } catch (Exception e) {
            logger.error("❌ Ошибка при отправке сообщения: {}", e.getMessage(), e);
            dialogDisplayer.showTimedErrorAlert("Ошибка", "Не удалось отправить сообщение", 3);
            updateStatus("❌ Ошибка отправки");
        }
    }

    @FXML
    private void handleImageSend() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите изображение");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File selectedFile = fileChooser.showOpenDialog(messageTextArea.getScene().getWindow());

        if (selectedFile != null) {
            try {
                byte[] imageData = Files.readAllBytes(selectedFile.toPath());
                chatService.sendImage(imageData, selectedFile.getName());

                // Показываем превью отправленного изображения
                displayImageMessage(imageData, selectedFile.getName(), true);
                updateStatus("✅ Изображение отправлено");

            } catch (IOException e) {
                logger.error("❌ Ошибка чтения файла: {}", e.getMessage());
                dialogDisplayer.showErrorDialog("Ошибка загрузки изображения: " + e.getMessage());
            } catch (Exception e) {
                logger.error("❌ Ошибка отправки изображения: {}", e.getMessage());
                dialogDisplayer.showErrorDialog("Ошибка отправки изображения: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleFileSend() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите файл");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*.*"));

        File selectedFile = fileChooser.showOpenDialog(messageTextArea.getScene().getWindow());

        if (selectedFile != null) {
            try {
                byte[] fileData = Files.readAllBytes(selectedFile.toPath());
                chatService.sendFile(fileData, selectedFile.getName());

                displayFileMessage(selectedFile.getName(), fileData.length, true);
                updateStatus("✅ Файл отправлен");

            } catch (IOException e) {
                logger.error("❌ Ошибка чтения файла: {}", e.getMessage());
                dialogDisplayer.showErrorDialog("Ошибка загрузки файла: " + e.getMessage());
            } catch (Exception e) {
                logger.error("❌ Ошибка отправки файла: {}", e.getMessage());
                dialogDisplayer.showErrorDialog("Ошибка отправки файла: " + e.getMessage());
            }
        }
    }

    // 🔴 ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ ОТОБРАЖЕНИЯ UI
    private void displayTextMessage(String sender, String text, LocalDateTime timestamp, boolean isOwnMessage) {
        hideNoMessagesHint();

        HBox messageBox = new HBox();
        messageBox.getStyleClass().add("message-box");
        messageBox.getStyleClass().add(isOwnMessage ? "own-message" : "received-message");
        messageBox.setAlignment(isOwnMessage ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        VBox messageContent = new VBox();
        messageContent.getStyleClass().add("message-content");
        messageContent.setMaxWidth(400);

        // Текст сообщения
        TextFlow textFlow = new TextFlow();
        Text textNode = new Text(text);
        textNode.getStyleClass().add("message-text");
        textFlow.getChildren().add(textNode);

        // Время и отправитель
        HBox messageMeta = new HBox();
        messageMeta.getStyleClass().add("message-meta");

        Label timeLabel = new Label(timestamp.format(TIME_FORMATTER));
        timeLabel.getStyleClass().add("message-time");

        Label senderLabel = new Label(isOwnMessage ? "Вы" : sender);
        senderLabel.getStyleClass().add("message-sender");

        messageMeta.getChildren().addAll(timeLabel, senderLabel);
        HBox.setHgrow(timeLabel, Priority.ALWAYS);

        messageContent.getChildren().addAll(textFlow, messageMeta);
        messageBox.getChildren().add(messageContent);

        messagesContainer.getChildren().add(messageBox);
        scrollToBottom();
    }

    private void displayImageMessage(byte[] imageData, String fileName, boolean isOwnMessage) {
        hideNoMessagesHint();

        HBox messageBox = new HBox();
        messageBox.getStyleClass().add("message-box");
        messageBox.getStyleClass().add(isOwnMessage ? "own-message" : "received-message");
        messageBox.setAlignment(isOwnMessage ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        try {
            Image image = new Image(new ByteArrayInputStream(imageData));
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(250);
            imageView.setFitHeight(200);
            imageView.setPreserveRatio(true);
            imageView.getStyleClass().add("message-image");

            VBox imageContent = new VBox(5, imageView);
            imageContent.getStyleClass().add("image-message-content");

            messageBox.getChildren().add(imageContent);
            messagesContainer.getChildren().add(messageBox);
            scrollToBottom();

        } catch (Exception e) {
            logger.error("❌ Ошибка отображения изображения: {}", e.getMessage());
            displayTextMessage("Система", "Не удалось загрузить изображение: " + fileName,
                    LocalDateTime.now(), isOwnMessage);
        }
    }

    private void displayFileMessage(String fileName, long fileSize, boolean isOwnMessage) {
        hideNoMessagesHint();

        HBox messageBox = new HBox();
        messageBox.getStyleClass().add("message-box");
        messageBox.getStyleClass().add(isOwnMessage ? "own-message" : "received-message");

        Label fileLabel = new Label("📎 " + fileName + " (" + formatFileSize(fileSize) + ")");
        fileLabel.getStyleClass().add("file-message");

        messageBox.getChildren().add(fileLabel);
        messagesContainer.getChildren().add(messageBox);
        scrollToBottom();
    }

    private void showFileSaveDialog(String fileName, byte[] fileData, String fileType) {
        boolean saveFile = dialogDisplayer.showConfirmationDialog(
                "Получен " + fileType,
                "Новый " + fileType,
                String.format("Получен %s '%s'\n\nСохранить файл?", fileType, fileName),
                "Сохранить",
                "Пропустить"
        );

        if (saveFile) {
            saveReceivedFile(fileName, fileData);
        }
    }

    private void saveReceivedFile(String fileName, byte[] fileData) {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Сохранить файл");
            fileChooser.setInitialFileName(fileName);

            File file = fileChooser.showSaveDialog(backButton.getScene().getWindow());
            if (file != null) {
                Files.write(file.toPath(), fileData);
                dialogDisplayer.showSuccessDialog("Файл сохранен: " + file.getName());
            }
        } catch (Exception e) {
            logger.error("❌ Ошибка сохранения файла: {}", e.getMessage());
            dialogDisplayer.showErrorDialog("Ошибка сохранения файла");
        }
    }

    // 🔴 ОСТАЛЬНЫЕ МЕТОДЫ (handleBack, showEncryptionInfo, updateStatus и т.д.)
    // остаются без изменений, так как они чисто UI-логика

    @FXML
    private void handleBack() {
        try {
            boolean confirmed = dialogDisplayer.showConfirmationDialog(
                    "Выход из чата",
                    "Вы уверены, что хотите выйти из чата?",
                    "Соединение будет разорвано.",
                    "ОК", "Отмена"
            );

            if (confirmed) {
                cleanup();
                sceneManager.showDevicesPanel();
            }
        } catch (Exception ex) {
            logger.error("❌ Ошибка при выходе из чата: {}", ex.getMessage(), ex);
            dialogDisplayer.showErrorDialog("Ошибка выхода: " + ex.getMessage());
        }
    }

    private void showEncryptionInfo() {
        String info = """
                🔒 Информация о шифровании:
                
                • Все сообщения шифруются перед отправкой
                • Используется AES-256 шифрование
                • Ключи генерируются автоматически
                • Подключение защищено
                
                Статус: Активно ✅
                """;

        dialogDisplayer.showAlert("Информация о шифровании", info);
    }

    private void showNoMessagesHint() {
        noMessagesPane.setVisible(true);
        messagesContainer.setVisible(false);
    }

    private void hideNoMessagesHint() {
        noMessagesPane.setVisible(false);
        messagesContainer.setVisible(true);
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            messagesScrollPane.setVvalue(1.0);
        });
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void updateStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText("Статус: " + status);
        }
    }

    public void cleanup() {
        chatService.disconnect();
        logger.info("🧹 ChatController очищен");
    }
}