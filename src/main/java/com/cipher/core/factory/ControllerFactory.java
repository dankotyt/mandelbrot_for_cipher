package com.cipher.core.factory;

import javafx.util.Callback;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

//@Component
//@RequiredArgsConstructor
//public class ControllerFactory {
//
//    private final ExecutorService executorService;
//    private final SeedServiceImpl seedService;
//    private final ClientAuthServiceImpl clientAuthService;
//    private final DialogDisplayer dialogDisplayer;
//    private final SceneManager sceneManager;
//    private final TempFileManager tempFileManager;
//    private final MandelbrotService mandelbrotService;
//    private final ImageEncrypt imageEncrypt;
//    private final ImageDecrypt imageDecrypt;
//    private final BinaryFile binaryFile;
//    private final EncryptionService encryptionService;
//    private final NumberFilter numberFilter;
//
//    public Object createController(Class<?> type) {
//        if (type == LoadingController.class) {
//            return new LoadingController(executorService, sceneManager);
//        } else if (type == StartController.class) {
//            return new StartController(sceneManager, executorService, dialogDisplayer);
//        } else if (type == ConnectionController.class) {
//            return new ConnectionController(seedService, sceneManager, dialogDisplayer);
//        } else if (type == SeedGenerationController.class) {
//            return new SeedGenerationController(sceneManager, seedService, dialogDisplayer);
//        } else if (type == LoginController.class) {
//            return new LoginController(sceneManager, clientAuthService, dialogDisplayer);
//        } else if (type == AccountController.class) {
//            return new AccountController(sceneManager);
//        } else if (type == EncryptBeginController.class) {
//            return new EncryptBeginController(sceneManager, dialogDisplayer, tempFileManager);
//        } else if (type == EncryptLoadController.class) {
//            return new EncryptLoadController(sceneManager, dialogDisplayer);
//        } else if (type == EncryptModeController.class) {
//            return new EncryptModeController(sceneManager, tempFileManager);
//        } else if (type == EncryptGenerateController.class) {
//            return new EncryptGenerateController(sceneManager, tempFileManager, dialogDisplayer, mandelbrotService);
//        } else if (type == EncryptChooseAreaController.class) {
//            return new EncryptChooseAreaController(sceneManager, tempFileManager, dialogDisplayer, imageEncrypt);
//        } else if (type == EncryptGenerateParamsController.class) {
//            return new EncryptGenerateParamsController(sceneManager, tempFileManager, dialogDisplayer,
//                    mandelbrotService, binaryFile);
//        } else if (type == EncryptManualController.class) {
//            return new EncryptManualController(sceneManager, tempFileManager, dialogDisplayer,
//                    encryptionService, numberFilter);
//        } else if (type == EncryptFinalController.class) {
//            return new EncryptFinalController(sceneManager, tempFileManager, dialogDisplayer, imageEncrypt);
//        } else if (type == EncryptFinalSelectedController.class) {
//            return new EncryptFinalSelectedController(sceneManager, tempFileManager, dialogDisplayer);
//        } else if (type == DecryptBeginController.class) {
//            return new DecryptBeginController(sceneManager, tempFileManager, dialogDisplayer);
//        } else if (type == DecryptLoadController.class) {
//            return new DecryptLoadController(sceneManager, dialogDisplayer);
//        } else if (type == DecryptModeController.class) {
//            return new DecryptModeController(sceneManager, tempFileManager, dialogDisplayer);
//        } else if (type == DecryptFinalController.class) {
//            return new DecryptFinalController(sceneManager, tempFileManager, dialogDisplayer, imageDecrypt);
//        }
//        return null;
//    }
//}

@Component
@RequiredArgsConstructor
public class ControllerFactory implements Callback<Class<?>, Object> {

    private final ApplicationContext applicationContext;

    @Override
    public Object call(Class<?> type) {
        return applicationContext.getBean(type);
    }
}
