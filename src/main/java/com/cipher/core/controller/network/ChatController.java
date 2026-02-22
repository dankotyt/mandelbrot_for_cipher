package com.cipher.core.controller.network;

import com.cipher.client.service.chat.ChatHistoryService;
import com.cipher.client.service.chat.ChatService;
import com.cipher.common.dto.chat.ChatMessageDTO;
import com.cipher.core.dto.DeviceDTO;
import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import com.cipher.core.utils.TempFileManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Controller
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

    private final SceneManager sceneManager;
    private final DialogDisplayer dialogDisplayer;
    private final ChatService chatService;
    private final TempFileManager tempFileManager;
    private final ChatHistoryService chatHistoryService;

    private boolean isInitialized = false;

    private String remoteDeviceName;
    private String remoteDeviceIp;

    @FXML
    public void initialize() {
        try {
            if (!isInitialized) {
                logger.info("=== ИНИЦИАЛИЗАЦИЯ НОВОГО ChatController ===");
                chatService.addListener(this);
            }
            logger.info("Хэш-код контроллера: {}", System.identityHashCode(this));
            logger.info("ChatHistoryService хэш: {}", System.identityHashCode(chatHistoryService));

            setupEventHandlers();
            setupUI();

            logger.info("ChatController инициализирован успешно, хэш: {}", System.identityHashCode(this));
            isInitialized = true;
        } catch (Exception e) {
            logger.error("Ошибка инициализации ChatController: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Устанавливает информацию о подключении к пиру
     */
    public void setConnectionInfo(String deviceName, String deviceIp) {
        logger.info("=== setConnectionInfo вызван ===");
        logger.info("Устройство: {}, IP: {}", deviceName, deviceIp);
        logger.info("Текущий remoteDeviceIp был: {}, станет: {}", this.remoteDeviceIp, deviceIp);
        logger.info("Хэш контроллера: {}", System.identityHashCode(this));

        this.remoteDeviceName = deviceName;
        this.remoteDeviceIp = deviceIp;

        Platform.runLater(() -> {
            logger.info("Platform.runLater в setConnectionInfo - начало");

            chatTitleLabel.setText("Чат с " + deviceName);
            connectionInfoLabel.setText("IP: " + deviceIp);

            // Устанавливаем текущий чат в истории и восстанавливаем сообщения
            logger.info("Устанавливаем текущий чат в истории: {}", deviceIp);
            chatHistoryService.setCurrentChat(deviceIp);

            // Проверяем, есть ли сообщения в истории
            List<HBox> history = chatHistoryService.getMessages(deviceIp);
            logger.info("В истории найдено сообщений для {}: {}", deviceIp, history.size());

            if (!history.isEmpty()) {
                logger.info("Будет восстановлено {} сообщений", history.size());
                for (int i = 0; i < history.size(); i++) {
                    logger.debug("Сообщение {}: {}", i, history.get(i).getStyleClass());
                }
            } else {
                logger.warn("История пуста для IP: {}", deviceIp);

            }

            restoreChatHistory();

            if (chatService.isConnected()) {
                String currentPeer = chatService.getConnectedPeer();
                if (deviceIp.equals(currentPeer)) {
                    updateStatus("P2P соединение уже установлено ✓");
                    encryptionStatusLabel.setText("Шифрование: Активно");
                    logger.info("Уже подключены к {}", deviceIp);
                    return;
                }
            }

            updateStatus("Установка P2P соединения...");
            logger.info("Platform.runLater в setConnectionInfo - конец");
        });

        logger.info("setConnectionInfo завершен");
    }

    private void restoreChatHistory() {
        logger.info("=== restoreChatHistory вызван ===");
        logger.info("remoteDeviceIp: {}, хэш контроллера: {}", remoteDeviceIp, System.identityHashCode(this));

        if (remoteDeviceIp == null) {
            logger.error("remoteDeviceIp = null, невозможно восстановить историю");
            return;
        }

        List<HBox> history = chatHistoryService.getMessages(remoteDeviceIp);
        logger.info("Получено {} сообщений из ChatHistoryService для IP {}", history.size(), remoteDeviceIp);

        if (!history.isEmpty()) {
            logger.info("Восстанавливаем сообщения в UI...");
            hideNoMessagesHint();

            int beforeCount = messagesContainer.getChildren().size();
            messagesContainer.getChildren().addAll(history);
            int afterCount = messagesContainer.getChildren().size();

            logger.info("До восстановления: {} элементов, после: {} элементов", beforeCount, afterCount);

            // Прокрутка вниз после восстановления
            Platform.runLater(() -> {
                messagesScrollPane.setVvalue(1.0);
                logger.info("Прокрутка вниз выполнена");
            });

            logger.info("Восстановлено {} сообщений для чата с {}", history.size(), remoteDeviceIp);
        } else {
            logger.warn("Нет сообщений для восстановления для IP: {}", remoteDeviceIp);
            showNoMessagesHint();
        }

        logger.info("restoreChatHistory завершен");
    }

    private boolean isCurrentChat() {
        String currentChatId = chatHistoryService.getCurrentChatId();
        boolean isCurrent = remoteDeviceIp != null && remoteDeviceIp.equals(currentChatId);

        logger.debug("isCurrentChat проверка: remoteIp={}, currentChatId={}, результат={}",
                remoteDeviceIp, currentChatId, isCurrent);

        return isCurrent;
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

    private void setupEventHandlers() {
        backButton.setOnAction(e -> handleBack());
        sendButton.setOnAction(e -> handleSendMessage());
        fileButton.setOnAction(e -> handleFileSend());
        imageButton.setOnAction(e -> sceneManager.showEncryptBeginPanel());
        clearChatButton.setOnAction(e -> handleClearChat());
        encryptionInfoButton.setOnAction(e -> showEncryptionInfo());

        messageTextArea.setOnKeyPressed(event -> {
            if (Objects.requireNonNull(event.getCode()) == KeyCode.ENTER) {
                if (event.isShiftDown()) {
                    messageTextArea.appendText("\n");
                } else {
                    event.consume();
                    handleSendMessage();
                }
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

                displayFileMessage(fileData, selectedFile.getName(), fileData.length, true);
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
                "Вы уверены, что хотите очистить историю сообщений? История очиститься только у вас!",
                "Это действие нельзя отменить.",
                "ОК", "Отмена"
        );

        if (confirmed) {
            logger.info("Очистка чата для IP: {}", remoteDeviceIp);

            // Очищаем историю в сервисе
            if (remoteDeviceIp != null) {
                chatHistoryService.clearChat(remoteDeviceIp);
                logger.info("История очищена в ChatHistoryService для {}", remoteDeviceIp);
            }

            messagesContainer.getChildren().clear();
            showNoMessagesHint();
            logger.info("История чата очищена в UI");
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
            logger.info("Получено текстовое сообщение от {}", message.getSender());
            displayTextMessage(message.getContent(), false);
            updateStatus("Новое сообщение");
        });
    }

    @Override
    public void onImageReceived(ChatMessageDTO imageMessage) {
        Platform.runLater(() -> {
            logger.info("Получено изображение: {}", imageMessage.getFileName());
            displayImageMessage(imageMessage.getFileData(), imageMessage.getFileName(), false);
            updateStatus("Получено изображение");
        });
    }

    @Override
    public void onFileReceived(ChatMessageDTO fileMessage) {
        Platform.runLater(() -> {
            logger.info("Получен файл: {}", fileMessage.getFileName());
            displayFileMessage(
                    fileMessage.getFileData(),
                    fileMessage.getFileName(),
                    fileMessage.getFileSize(),
                    false
            );
            updateStatus("Получен файл");
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
                chatHistoryService.clearChat(peerInfo);
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
        logger.info("=== displayTextMessage ===");
        logger.info("Текст: {}, isOwnMessage: {}, remoteDeviceIp: {}",
                text.length() > 20 ? text.substring(0, 20) + "..." : text,
                isOwnMessage, remoteDeviceIp);
        logger.info("Хэш контроллера: {}", System.identityHashCode(this));

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

        // Сохраняем в историю
        if (remoteDeviceIp != null) {
            logger.info("Сохраняем сообщение в историю для IP: {}", remoteDeviceIp);
            chatHistoryService.addMessage(remoteDeviceIp, messageBox);

            // Проверяем, что сообщение сохранилось
            List<HBox> history = chatHistoryService.getMessages(remoteDeviceIp);
            logger.info("После сохранения в истории {} сообщений", history.size());
        } else {
            logger.error("remoteDeviceIp = null, сообщение НЕ сохранено в истории!");
        }

        // Если это текущий чат - отображаем
        if (isCurrentChat()) {
            logger.info("Это текущий чат, отображаем сообщение");
            messagesContainer.getChildren().add(messageBox);

            // Автопрокрутка к новому сообщению
            Platform.runLater(() -> {
                messagesScrollPane.setVvalue(1.0);
            });
        } else {
            logger.warn("Это НЕ текущий чат, сообщение скрыто. currentChatId={}",
                    chatHistoryService.getCurrentChatId());
        }

        logger.info("displayTextMessage завершен");
    }

    private void displayImageMessage(byte[] imageData, String fileName, boolean isOwnMessage) {
        logger.info("=== displayImageMessage ===");
        logger.info("Файл: {}, размер: {}, isOwnMessage: {}, remoteDeviceIp: {}",
                fileName, imageData.length, isOwnMessage, remoteDeviceIp);

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

            imageView.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) { // двойной клик
                    saveImageToDevice(imageData, fileName);
                }
            });

            Label fileNameLabel = new Label(fileName);
            fileNameLabel.getStyleClass().add("image-file-name");

            Label fileSizeLabel = new Label(formatFileSize(imageData.length));
            fileSizeLabel.getStyleClass().add("image-file-size");

            VBox fileInfo = new VBox(2, fileNameLabel, fileSizeLabel);
            fileInfo.getStyleClass().add("file-info");

            messageContent.getChildren().addAll(imageView, fileInfo);
            messageBox.getChildren().add(messageContent);

            // Сохраняем в историю
            if (remoteDeviceIp != null) {
                logger.info("Сохраняем изображение в историю для IP: {}", remoteDeviceIp);
                chatHistoryService.addMessage(remoteDeviceIp, messageBox);

                List<HBox> history = chatHistoryService.getMessages(remoteDeviceIp);
                logger.info("После сохранения в истории {} сообщений", history.size());
            } else {
                logger.error("remoteDeviceIp = null, изображение НЕ сохранено в истории!");
            }

            // Если это текущий чат - отображаем
            if (isCurrentChat()) {
                logger.info("Это текущий чат, отображаем изображение");
                messagesContainer.getChildren().add(messageBox);

                Platform.runLater(() -> {
                    messagesScrollPane.setVvalue(1.0);
                });
            } else {
                logger.warn("Это НЕ текущий чат, изображение скрыто. currentChatId={}",
                        chatHistoryService.getCurrentChatId());
            }

        } catch (Exception e) {
            logger.error("Ошибка отображения изображения: {}", e.getMessage(), e);
            // Показываем сообщение об ошибке
            displayTextMessage("Не удалось загрузить изображение: " + fileName, isOwnMessage);
        }
    }

    private void displayFileMessage(byte[] fileData, String fileName, long fileSize, boolean isOwnMessage) {
        logger.info("=== displayFileMessage ===");
        logger.info("Файл: {}, размер: {}, isOwnMessage: {}, remoteDeviceIp: {}",
                fileName, fileSize, isOwnMessage, remoteDeviceIp);

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

        // Добавляем обработчик клика для сохранения файла
        PauseTransition pause = new PauseTransition(Duration.millis(350));

        fileContent.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                pause.stop();
                handleDoubleClick(fileData, fileName);
            } else if (event.getClickCount() == 1) {
                pause.setOnFinished(e -> handleSingleClick(fileData, fileName));
                pause.play();
            }
            event.consume();
        });

        messageContent.getChildren().add(fileContent);
        messageBox.getChildren().add(messageContent);

        // Сохраняем в историю
        if (remoteDeviceIp != null) {
            logger.info("Сохраняем файл в историю для IP: {}", remoteDeviceIp);
            chatHistoryService.addMessage(remoteDeviceIp, messageBox);

            List<HBox> history = chatHistoryService.getMessages(remoteDeviceIp);
            logger.info("После сохранения в истории {} сообщений", history.size());
        } else {
            logger.error("remoteDeviceIp = null, файл НЕ сохранен в истории!");
        }

        // Если это текущий чат - отображаем
        if (isCurrentChat()) {
            logger.info("Это текущий чат, отображаем файл");
            messagesContainer.getChildren().add(messageBox);

            Platform.runLater(() -> {
                messagesScrollPane.setVvalue(1.0);
            });
        } else {
            logger.warn("Это НЕ текущий чат, файл скрыт. currentChatId={}",
                    chatHistoryService.getCurrentChatId());
        }
    }

    private void handleSingleClick(byte[] fileData, String fileName) {
        if (fileName.endsWith(".bin")) {
            try {
                File tempFile = new File(tempFileManager.getTempPath() + fileName);
                Files.write(tempFile.toPath(), fileData);
                sceneManager.showDecryptBeginPanel();
                logger.info("Открыт экран дешифрования для файла: {}", fileName);
            } catch (IOException e) {
                logger.error("Ошибка при сохранении временного файла", e);
                dialogDisplayer.showErrorDialog("Не удалось открыть файл для дешифрования");
            }
        }
    }

    private void handleDoubleClick(byte[] fileData, String fileName) {
        saveFileToDevice(fileData, fileName);
    }

    private void saveFileToDevice(byte[] fileData, String fileName) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить файл");
        fileChooser.setInitialFileName(fileName);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All Files", "*.*"));

        File file = fileChooser.showSaveDialog(messageTextArea.getScene().getWindow());
        if (file != null) {
            try {
                Files.write(file.toPath(), fileData);
                dialogDisplayer.showSuccessDialog("Файл сохранен: " + file.getAbsolutePath());
            } catch (IOException e) {
                dialogDisplayer.showErrorDialog("Не удалось сохранить файл");
            }
        }
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

    private void saveImageToDevice(byte[] imageData, String fileName) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить изображение");
        fileChooser.setInitialFileName(fileName);
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PNG", "*.png"),
                new FileChooser.ExtensionFilter("JPEG", "*.jpg", "*.jpeg")
        );

        // Используем любой доступный UI элемент для контекста
        File file = fileChooser.showSaveDialog(messageTextArea.getScene().getWindow());
        if (file != null) {
            try {
                Files.write(file.toPath(), imageData);
                dialogDisplayer.showSuccessDialog("Изображение сохранено: " + file.getAbsolutePath());
            } catch (IOException e) {
                dialogDisplayer.showErrorDialog("Не удалось сохранить изображение");
            }
        }
    }

    /**
     * Очистка ресурсов при закрытии чата
     */
    public void cleanup() {
        chatHistoryService.clearChat(remoteDeviceIp);
        chatService.removeListener(this);
        isInitialized = false;
        chatService.disconnect();
        logger.info("ChatController очищен, хэш: {}", System.identityHashCode(this));
    }
}