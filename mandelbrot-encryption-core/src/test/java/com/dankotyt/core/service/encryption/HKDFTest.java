package com.dankotyt.core.service.encryption;

import com.dankotyt.core.service.encryption.util.HKDF;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HKDFTest {

    @Test
    void extract_withValidInput_shouldReturnPrk() throws Exception {
        byte[] salt = "salt".getBytes(StandardCharsets.UTF_8);
        byte[] ikm = "ikm".getBytes(StandardCharsets.UTF_8);
        byte[] prk = HKDF.extract(salt, ikm);
        assertEquals(32, prk.length);
    }

    @Test
    void extract_withNullSalt_shouldUseDefault() throws Exception {
        byte[] ikm = "ikm".getBytes(StandardCharsets.UTF_8);
        byte[] prk = HKDF.extract(null, ikm);
        assertEquals(32, prk.length);
    }

    @Test
    void extract_withEmptySalt_shouldUseDefault() throws Exception {
        byte[] ikm = "ikm".getBytes(StandardCharsets.UTF_8);
        byte[] prk = HKDF.extract(new byte[0], ikm);
        assertEquals(32, prk.length);
    }

    @Test
    void extract_withNullIkm_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> HKDF.extract(new byte[16], null));
    }

    @Test
    void expand_withNullPrk_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> HKDF.expand(null, new byte[0], 32));
    }

    @Test
    void expand_withValidInput_shouldReturnCorrectLength() throws Exception {
        byte[] prk = new byte[32];
        byte[] info = "info".getBytes(StandardCharsets.UTF_8);
        int length = 64;
        byte[] okm = HKDF.expand(prk, info, length);
        assertEquals(length, okm.length);
    }

    @Test
    void expand_withZeroLength_shouldReturnEmpty() throws Exception {
        byte[] prk = new byte[32];
        byte[] info = "info".getBytes(StandardCharsets.UTF_8);
        byte[] okm = HKDF.expand(prk, info, 0);
        assertEquals(0, okm.length);
    }

    @Test
    void expand_withEmptyInfo_shouldWork() throws Exception {
        byte[] prk = new byte[32];
        byte[] okm = HKDF.expand(prk, new byte[0], 32);
        assertEquals(32, okm.length);
    }
}