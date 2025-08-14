package com.cipher;

import com.cipher.view.javafx.JavaFXImpl;
import javafx.application.Application;

import javax.swing.*;

public class Launcher {
    public static void main(String[] args) {
        try {
            Application.launch(JavaFXImpl.class, args);
        } catch (Throwable t) {
            JOptionPane.showMessageDialog(null,
                    "Launch failed:\n" + t.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}