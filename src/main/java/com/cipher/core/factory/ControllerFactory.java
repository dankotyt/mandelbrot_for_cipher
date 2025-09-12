package com.cipher.core.factory;

import com.cipher.client.service.impl.ClientAuthServiceImpl;
import com.cipher.client.service.impl.SeedServiceImpl;
import com.cipher.client.utils.NetworkUtils;
import com.cipher.core.controller.encrypt.*;
import com.cipher.core.controller.online.AccountController;
import com.cipher.core.controller.online.ConnectionController;
import com.cipher.core.controller.online.LoginController;
import com.cipher.core.controller.online.SeedGenerationController;
import com.cipher.core.controller.start.LoadingController;
import com.cipher.core.controller.start.StartController;
import com.cipher.core.encryption.ImageEncrypt;
import com.cipher.core.service.MandelbrotService;
import com.cipher.core.utils.BinaryFile;
import com.cipher.core.utils.DialogDisplayer;
import com.cipher.core.utils.SceneManager;
import com.cipher.core.utils.TempFileManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

@Component
@RequiredArgsConstructor
public class ControllerFactory {

    private final ExecutorService executorService;
    private final SeedServiceImpl seedService;
    private final ClientAuthServiceImpl clientAuthService;
    private final DialogDisplayer dialogDisplayer;
    private final SceneManager sceneManager;
    private final NetworkUtils networkUtils;
    private final TempFileManager tempFileManager;
    private final MandelbrotService mandelbrotService;
    private final ImageEncrypt imageEncrypt;
    private final BinaryFile binaryFile;
    // Добавьте другие необходимые сервисы

    public Object createController(Class<?> type) {
        if (type == LoadingController.class) {
            return new LoadingController(executorService, sceneManager);
        } else if (type == StartController.class) {
            return new StartController(sceneManager, executorService, seedService,
                    networkUtils, dialogDisplayer, tempFileManager);
        } else if (type == ConnectionController.class) {
            return new ConnectionController(seedService, sceneManager, dialogDisplayer);
        } else if (type == SeedGenerationController.class) {
            return new SeedGenerationController(sceneManager, seedService, dialogDisplayer);
        } else if (type == LoginController.class) {
            return new LoginController(sceneManager, clientAuthService, dialogDisplayer);
        } else if (type == AccountController.class) {
            return new AccountController(sceneManager);
        } else if (type == EncryptBeginController.class) {
            return new EncryptBeginController(sceneManager, dialogDisplayer, tempFileManager);
        } else if (type == EncryptLoadController.class) {
            return new EncryptLoadController(sceneManager, dialogDisplayer);
        } else if (type == EncryptModeController.class) {
            return new EncryptModeController(sceneManager, tempFileManager);
        } else if (type == EncryptGenerateController.class) {
            return new EncryptGenerateController(sceneManager, tempFileManager, dialogDisplayer, mandelbrotService);
        } else if (type == EncryptChooseAreaController.class) {
            return new EncryptChooseAreaController(sceneManager, tempFileManager, dialogDisplayer, imageEncrypt);
        } else if (type == EncryptGenerateParamsController.class) {
            return new EncryptGenerateParamsController(sceneManager, tempFileManager, dialogDisplayer,
                    mandelbrotService, binaryFile);
        }
        return null;
    }
}
