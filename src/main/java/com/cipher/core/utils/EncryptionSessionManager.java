package com.cipher.core.utils;

import com.cipher.core.dto.*;
import com.cipher.core.dto.neww.SegmentationParams;
import com.cipher.core.encryption.CryptographicService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class EncryptionSessionManager {
    private final CryptographicService cryptoService;

    private byte[] masterSeed;
    private EncryptionSessionParams sessionDTO;

    @Setter
    private SegmentationParams segmentationParams;
    @Setter
    private MandelbrotParams mandelbrotParams;

    /**
     * Инициализация новой сессии шифрования
     */
    public EncryptionSessionParams initializeSession() throws Exception {
        // Генерация мастер-сида
        this.masterSeed = new byte[32];
        new SecureRandom().nextBytes(masterSeed);

        // Шифрование мастер-сида для передачи
        EncryptionResult encryptionResult = cryptoService.encryptData(masterSeed, masterSeed);

        this.sessionDTO = new EncryptionSessionParams(
                encryptionResult.encryptedData(),
                encryptionResult.iv(),
                encryptionResult.salt(),
                CryptographicService.getALGORITHM(),
                CryptographicService.getKEY_SIZE(),
                System.currentTimeMillis()
        );

        return sessionDTO;
    }

    /**
     * Безопасное получение мастер-сида (только для внутреннего использования)
     */
    public byte[] getMasterSeed() {
        return masterSeed != null ? masterSeed.clone() : null;
    }

    /**
     * Создание DTO для дешифровки
     */
    public KeyDecoderParams createKeyDecoderParams() {
        return new KeyDecoderParams(
                mandelbrotParams,
                segmentationParams,
                sessionDTO.encryptedMasterSeed(),
                sessionDTO.iv(),
                sessionDTO.salt()
        );
    }

    /**
     * Безопасное завершение сессии
     */
    public void clearSession() {
        if (masterSeed != null) {
            Arrays.fill(masterSeed, (byte) 0);
            masterSeed = null;
        }
        sessionDTO = null;
        segmentationParams = null;
        mandelbrotParams = null;
    }
}
