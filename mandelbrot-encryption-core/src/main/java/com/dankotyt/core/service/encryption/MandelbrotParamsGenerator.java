package com.dankotyt.core.service.encryption;

import com.dankotyt.core.dto.MandelbrotParams;

import java.security.SecureRandom;

public interface MandelbrotParamsGenerator {
    MandelbrotParams generate(SecureRandom prng);
}
