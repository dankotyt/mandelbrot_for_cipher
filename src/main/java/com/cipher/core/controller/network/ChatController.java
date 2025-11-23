package com.cipher.core.controller.network;

import com.cipher.client.service.chat.ChatService;
import com.cipher.common.dto.chat.ChatMessageDTO;
import com.cipher.common.utils.NetworkConstants;
import com.cipher.core.TestEncryptionRunner;
import com.cipher.core.dto.DeviceDTO;
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
import javafx.stage.Stage;
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

import static com.cipher.common.utils.NetworkConstants.CHAT_PORT;

@Controller
@Scope("prototype")
@RequiredArgsConstructor
public class ChatController implements ChatService.ChatListener {
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

    @FXML private Button testButton;

    private final SceneManager sceneManager;
    private final DialogDisplayer dialogDisplayer;
    private final ChatService chatService;
    private final TestEncryptionRunner testEncryptionRunner;

    private String remoteDeviceName;
    private String remoteDeviceIp;

    @FXML
    public void initialize() {
        try {
            logger.info("Инициализация ChatController");

            setupEventHandlers();
            chatService.addListener(this);
            setupUI();

            logger.info("ChatController инициализирован успешно");

        } catch (Exception e) {
            logger.error("Ошибка инициализации ChatController: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Устанавливает информацию о подключении к пиру
     */
    public void setConnectionInfo(String deviceName, String deviceIp) {
        this.remoteDeviceName = deviceName;
        this.remoteDeviceIp = deviceIp;

        Platform.runLater(() -> {
            chatTitleLabel.setText("Чат с " + deviceName);
            connectionInfoLabel.setText("IP: " + deviceIp);
            updateStatus("Установка P2P соединения...");

            // Автоматически устанавливаем P2P соединение
            new Thread(() -> {
                try {
                    Thread.sleep(500); // Даем время на инициализацию UI

                    boolean connected = chatService.connectToPeer(deviceIp);

                    Platform.runLater(() -> {
                        if (connected) {
                            updateStatus("P2P соединение установлено ✓");
                            encryptionStatusLabel.setText("Шифрование: Активно");
                        } else {
                            updateStatus("Ожидание P2P соединения...");
                            // Автоповтор через 3 секунды
                            attemptAutoReconnection();
                        }
                    });

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        });
    }

    private void attemptAutoReconnection() {
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                if (!chatService.isConnected() && remoteDeviceIp != null) {
                    logger.info("Автоповтор подключения к: {}", remoteDeviceIp);
                    boolean reconnected = chatService.connectToPeer(remoteDeviceIp);

                    Platform.runLater(() -> {
                        if (reconnected) {
                            updateStatus("P2P соединение установлено ✓");
                            encryptionStatusLabel.setText("Шифрование: Активно");
                        } else {
                            updateStatus("Ожидание P2P соединения...");
                            attemptAutoReconnection();
                        }
                    });
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    @FXML
    private void handleRunEncryptionTest() {
        try {
            Stage primaryStage = (Stage) backButton.getScene().getWindow();
            testEncryptionRunner.runEncryptionTest(primaryStage);
        } catch (Exception e) {
            logger.error("Ошибка запуска теста: {}", e.getMessage());
            dialogDisplayer.showErrorDialog("Ошибка запуска теста: " + e.getMessage());
        }
    }

    private void setupEventHandlers() {
        backButton.setOnAction(e -> handleBack());
        sendButton.setOnAction(e -> handleSendMessage());
        fileButton.setOnAction(e -> handleFileSend());
        imageButton.setOnAction(e -> sceneManager.showEncryptBeginPanel());
        clearChatButton.setOnAction(e -> handleClearChat());
        encryptionInfoButton.setOnAction(e -> showEncryptionInfo());

        testButton.setOnAction(e -> handleRunEncryptionTest());

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
        messagesContainer.heightProperty().addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> {
                messagesScrollPane.setVvalue(1.0);
            });
        });

        showNoMessagesHint();
    }

    @FXML
    private void handleSendMessage() {
        String messageText = messageTextArea.getText().trim();

        if (messageText.isEmpty()) {
            return;
        }

        // Простая проверка соединения
        if (!chatService.isConnected()) {
            dialogDisplayer.showTimedErrorAlert("Ошибка", "Соединение не установлено", 3);
            updateStatus("Ожидание подключения...");
            attemptAutoReconnection();
            return;
        }

        try {
            chatService.sendMessage(messageText);

            // Отображаем сообщение локально
            displayTextMessage(messageText, true);

            messageTextArea.clear();
            updateStatus("Сообщение отправлено ✓");

        } catch (Exception e) {
            logger.error("Ошибка при отправке сообщения: {}", e.getMessage(), e);
            dialogDisplayer.showTimedErrorAlert("Ошибка", "Не удалось отправить сообщение", 3);
            updateStatus("Ошибка отправки");
            attemptAutoReconnection();
        }
    }

    @FXML
    private void handleImageSend() {
        if (!chatService.isConnected()) {
            dialogDisplayer.showTimedErrorAlert("Ошибка", "Соединение не установлено", 3);
            updateStatus("Ошибка: соединение не подтверждено");
            return;
        }

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
                updateStatus("Изображение отправлено");

            } catch (IOException e) {
                logger.error("Ошибка чтения файла: {}", e.getMessage());
                dialogDisplayer.showErrorDialog("Ошибка загрузки изображения: " + e.getMessage());
            } catch (Exception e) {
                logger.error("Ошибка отправки изображения: {}", e.getMessage());
                dialogDisplayer.showErrorDialog("Ошибка отправки изображения: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleFileSend() {
        if (!chatService.isConnected()) {
            dialogDisplayer.showTimedErrorAlert("Ошибка", "Соединение не установлено", 3);
            updateStatus("Ошибка: соединение не подтверждено");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Выберите файл");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*.*"));

        File selectedFile = fileChooser.showOpenDialog(messageTextArea.getScene().getWindow());

        if (selectedFile != null) {
            try {
                byte[] fileData = Files.readAllBytes(selectedFile.toPath());
                chatService.sendFile(fileData, selectedFile.getName());

                displayFileMessage(selectedFile.getName(), fileData.length, true);
                updateStatus("Файл отправлен");

            } catch (IOException e) {
                logger.error("Ошибка чтения файла: {}", e.getMessage());
                dialogDisplayer.showErrorDialog("Ошибка загрузки файла: " + e.getMessage());
            } catch (Exception e) {
                logger.error("Ошибка отправки файла: {}", e.getMessage());
                dialogDisplayer.showErrorDialog("Ошибка отправки файла: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleClearChat() {
        if (messagesContainer.getChildren().isEmpty()) {
            return;
        }

        boolean confirmed = dialogDisplayer.showConfirmationDialog(
                "Очистка чата",
                "Вы уверены, что хотите очистить историю сообщений?",
                "Это действие нельзя отменить.",
                "ОК", "Отмена"
        );

        if (confirmed) {
            messagesContainer.getChildren().clear();
            showNoMessagesHint();
            logger.info("История чата очищена");
        }
    }

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
            logger.error("Ошибка при выходе из чата: {}", ex.getMessage(), ex);
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

    @Override
    public void onMessageReceived(ChatMessageDTO message) {
        Platform.runLater(() -> {
            displayTextMessage(message.getContent(), false);
            updateStatus("Новое сообщение");
        });
    }

    @Override
    public void onImageReceived(ChatMessageDTO imageMessage) {
        Platform.runLater(() -> {
            displayImageMessage(imageMessage.getFileData(), imageMessage.getFileName(), false);
            updateStatus("Получено изображение");
        });
    }

    @Override
    public void onConnectionStatusChanged(boolean connected, String peerInfo) {
        Platform.runLater(() -> {
            if (connected) {
                updateStatus("Подключение установлено: " + peerInfo);
                encryptionStatusLabel.setText("Шифрование: Активно");
            } else {
                updateStatus("Соединение разорвано");
                encryptionStatusLabel.setText("Шифрование: Неактивно");
                dialogDisplayer.showTimedErrorAlert("Соединение потеряно",
                        "Соединение с удаленным устройством разорвано", 5);
            }
        });
    }

    @Override
    public void onError(String errorMessage) {
        Platform.runLater(() -> {
            updateStatus("Ошибка: " + errorMessage);
            dialogDisplayer.showTimedErrorAlert("Ошибка чата", errorMessage, 5);
        });
    }

    @Override
    public void onIncomingConnection(String peerIp) {
        logger.info("Входящее подключение от: {}", peerIp);
        // В P2P автоматически принимаем входящие подключения
        Platform.runLater(() -> {
            updateStatus("Входящее подключение от " + peerIp);
        });
    }

    @Override
    public void onIncomingChatConnection(String peerIp) {
        Platform.runLater(() -> {
            if (this.remoteDeviceIp == null) {
                try {
                    DeviceDTO device = new DeviceDTO("Peer", peerIp);
                    sceneManager.showChatPanel(device);
                    logger.info("✅ Чат автоматически открыт с входящим подключением: {}", peerIp);
                } catch (Exception e) {
                    logger.error("❌ Ошибка автоматического открытия чата: {}", e.getMessage());
                }
            }
        });
    }

    private void displayTextMessage(String text, boolean isOwnMessage) {
        hideNoMessagesHint();

        HBox messageBox = new HBox();
        messageBox.getStyleClass().add("message-box");

        if (isOwnMessage) {
            messageBox.getStyleClass().add("own-message");
            messageBox.setAlignment(Pos.CENTER_RIGHT);
        } else {
            messageBox.getStyleClass().add("received-message");
            messageBox.setAlignment(Pos.CENTER_LEFT);
        }

        VBox messageContent = new VBox();
        messageContent.getStyleClass().add("message-content");
        messageBox.setMaxWidth(400);

        // Текст сообщения
        TextFlow textFlow = new TextFlow();
        Text textNode = new Text(text);
        textNode.getStyleClass().add("message-text");
        textFlow.getChildren().add(textNode);

        // Время и статус
        HBox messageMeta = new HBox();
        messageMeta.getStyleClass().add("message-meta");

        Label timeLabel = new Label(LocalDateTime.now().format(TIME_FORMATTER));
        timeLabel.getStyleClass().add("message-time");

        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("message-status");

        if (isOwnMessage) {
            statusLabel.setText("✓");
        } else {
            statusLabel.setText(remoteDeviceName != null ? remoteDeviceName : "Peer");
        }

        messageMeta.getChildren().addAll(timeLabel, statusLabel);
        HBox.setHgrow(timeLabel, Priority.ALWAYS);

        messageContent.getChildren().addAll(textFlow, messageMeta);
        messageBox.getChildren().add(messageContent);

        messagesContainer.getChildren().add(messageBox);

        // Автопрокрутка к новому сообщению
        Platform.runLater(() -> {
            messagesScrollPane.setVvalue(1.0);
        });
    }

    private void displayImageMessage(byte[] imageData, String fileName, boolean isOwnMessage) {
        hideNoMessagesHint();

        HBox messageBox = new HBox();
        messageBox.getStyleClass().add("message-box");

        if (isOwnMessage) {
            messageBox.getStyleClass().add("own-message");
            messageBox.setAlignment(Pos.CENTER_RIGHT);
        } else {
            messageBox.getStyleClass().add("received-message");
            messageBox.setAlignment(Pos.CENTER_LEFT);
        }

        VBox messageContent = new VBox();
        messageContent.getStyleClass().add("image-message-content");
        messageContent.setMaxWidth(300);

        try {
            Image image = new Image(new ByteArrayInputStream(imageData));
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(250);
            imageView.setFitHeight(200);
            imageView.setPreserveRatio(true);
            imageView.getStyleClass().add("message-image");

            Label fileNameLabel = new Label(fileName);
            fileNameLabel.getStyleClass().add("image-file-name");

            Label fileSizeLabel = new Label(formatFileSize(imageData.length));
            fileSizeLabel.getStyleClass().add("image-file-size");

            VBox fileInfo = new VBox(2, fileNameLabel, fileSizeLabel);
            fileInfo.getStyleClass().add("file-info");

            messageContent.getChildren().addAll(imageView, fileInfo);
            messageBox.getChildren().add(messageContent);

            messagesContainer.getChildren().add(messageBox);

        } catch (Exception e) {
            logger.error("Ошибка отображения изображения: {}", e.getMessage());
            // Показываем сообщение об ошибке
            displayTextMessage("Не удалось загрузить изображение: " + fileName, isOwnMessage);
        }
    }

    private void displayFileMessage(String fileName, long fileSize, boolean isOwnMessage) {
        hideNoMessagesHint();

        HBox messageBox = new HBox();
        messageBox.getStyleClass().add("message-box");

        if (isOwnMessage) {
            messageBox.getStyleClass().add("own-message");
            messageBox.setAlignment(Pos.CENTER_RIGHT);
        } else {
            messageBox.getStyleClass().add("received-message");
            messageBox.setAlignment(Pos.CENTER_LEFT);
        }

        VBox messageContent = new VBox();
        messageContent.getStyleClass().add("file-message-content");
        messageContent.setMaxWidth(300);

        // Иконка файла
        Label fileIcon = new Label("📎");
        fileIcon.getStyleClass().add("file-icon");

        // Информация о файле
        Label fileNameLabel = new Label(fileName);
        fileNameLabel.getStyleClass().add("file-name");

        Label fileSizeLabel = new Label(formatFileSize(fileSize));
        fileSizeLabel.getStyleClass().add("file-size");

        VBox fileInfo = new VBox(2, fileNameLabel, fileSizeLabel);
        fileInfo.getStyleClass().add("file-info");

        HBox fileContent = new HBox(10, fileIcon, fileInfo);
        fileContent.getStyleClass().add("file-content");
        fileContent.setAlignment(Pos.CENTER_LEFT);

        messageContent.getChildren().add(fileContent);
        messageBox.getChildren().add(messageContent);

        messagesContainer.getChildren().add(messageBox);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void showNoMessagesHint() {
        noMessagesPane.setVisible(true);
        messagesContainer.setVisible(false);
    }

    private void hideNoMessagesHint() {
        noMessagesPane.setVisible(false);
        messagesContainer.setVisible(true);
    }

    private void updateStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText("Статус: " + status);

            statusLabel.getStyleClass().removeAll(
                    "status-connected", "status-error", "status-waiting", "status-success"
            );

            if (status.contains("Ошибка") || status.contains("разорвано") || status.contains("потеряно")) {
                statusLabel.getStyleClass().add("status-error");
            } else if (status.contains("ожидание") || status.contains("подключение") || status.contains("Попытка")) {
                statusLabel.getStyleClass().add("status-waiting");
            } else if (status.contains("отправлено") || status.contains("установлено") || status.contains("успех")) {
                statusLabel.getStyleClass().add("status-success");
            } else {
                statusLabel.getStyleClass().add("status-connected");
            }
        }
    }

    /**
     * Очистка ресурсов при закрытии чата
     */
    public void cleanup() {
        chatService.removeListener(this);
        chatService.stopListening();
        chatService.disconnect();
        logger.info("ChatController очищен");
    }
}