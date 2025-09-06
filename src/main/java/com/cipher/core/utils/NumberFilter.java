package com.cipher.core.utils;

import javafx.scene.control.TextFormatter;
import org.springframework.stereotype.Component;

import java.util.function.UnaryOperator;

@Component
public class NumberFilter {
    public UnaryOperator<TextFormatter.Change> createIntegerFilter(int maxLength) {
        return change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty()) {
                return change;
            }
            if (newText.matches("([1-9]\\d*|0)?") && newText.length() <= maxLength) {
                return change;
            } else {
                return null;
            }
        };
    }

    // Метод для создания фильтра для чисел с плавающей точкой
    public UnaryOperator<TextFormatter.Change> createDoubleFilter() {
        return change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty()) {
                return change;
            }
            if (newText.matches("-?([1-9]\\d*|0)?(\\.\\d*)?") && newText.length() <= 15) {
                return change;
            } else {
                return null;
            }
        };
    }
}
