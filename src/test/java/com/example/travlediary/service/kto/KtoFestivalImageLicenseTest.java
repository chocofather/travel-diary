package com.example.travlediary.service.kto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KtoFestivalImageLicenseTest {

    @Test
    void mapsOnlySupportedTourApiCopyrightCodes() {
        assertThat(KtoFestivalImageLicense.fromCopyrightDivisionCode("Type1"))
                .contains(KtoFestivalImageLicense.KOGL_TYPE_1);
        assertThat(KtoFestivalImageLicense.fromCopyrightDivisionCode(" Type3 "))
                .contains(KtoFestivalImageLicense.KOGL_TYPE_3);
        assertThat(KtoFestivalImageLicense.fromCopyrightDivisionCode("Type2")).isEmpty();
        assertThat(KtoFestivalImageLicense.fromCopyrightDivisionCode(null)).isEmpty();
    }
}
