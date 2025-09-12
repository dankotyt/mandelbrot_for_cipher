package com.cipher.core.controller.start;

import com.cipher.core.utils.SceneManager;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressBar;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.ExecutorService;

@RequiredArgsConstructor
public class LoadingController {

    @FXML private ProgressBar progressBar;

    private final ExecutorService executorService;
    private final SceneManager sceneManager;

    @FXML
    public void initialize() {
        startLoadingTask();
    }

    private void startLoadingTask() {
        Task<Void> loadingTask = new Task<>() {
            @Override
            protected Void call() {
                for (int i = 0; i <= 100; i++) {
                    if (isCancelled()) break;
                    updateProgress(i, 100);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        if (isCancelled()) break;
                    }
                }
                return null;
            }
        };

        progressBar.progressProperty().bind(loadingTask.progressProperty());
        loadingTask.setOnSucceeded(event -> sceneManager.showStartPanel());
        executorService.execute(loadingTask);
    }
}
