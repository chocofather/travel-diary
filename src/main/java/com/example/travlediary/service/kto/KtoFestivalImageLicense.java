package com.example.travlediary.service.kto;

import java.util.Arrays;
import java.util.Optional;

public enum KtoFestivalImageLicense {
    KOGL_TYPE_1("Type1"),
    KOGL_TYPE_3("Type3");

    private final String tourApiCode;

    KtoFestivalImageLicense(String tourApiCode) {
        this.tourApiCode = tourApiCode;
    }

    public static Optional<KtoFestivalImageLicense> fromCopyrightDivisionCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.strip();
        return Arrays.stream(values())
                .filter(license -> license.tourApiCode.equalsIgnoreCase(normalized))
                .findFirst();
    }
}
