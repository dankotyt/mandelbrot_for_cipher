package com.cipher.core.utils;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConsoleManager {
    private static final Logger logger = LoggerFactory.getLogger(ConsoleManager.class);

    private static TextArea consoleTextArea;

    public static void setConsole(TextArea console) {
        consoleTextArea = console;
    }

    public static void log(String message) {
        if (consoleTextArea != null) {
            Platform.runLater(() -> {
                consoleTextArea.appendText(message + "\n");
                consoleTextArea.setScrollTop(Double.MAX_VALUE);
            });
        } else {
            logger.info("Console: {}", message);
        }
    }

    public static void clear() {
        if (consoleTextArea != null) {
            Platform.runLater(() -> consoleTextArea.clear());
        }
    }
}
