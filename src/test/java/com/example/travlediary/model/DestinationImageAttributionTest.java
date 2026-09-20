package com.example.travlediary.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationImageAttributionTest {

    @Test
    void existingImageWithoutAttributionCreatesNoAttributionState() {
        DestinationImage image = new DestinationImage();

        assertThat(image.isAttributionPresent()).isFalse();
        assertThat(image.getSafeSourceUrl()).isNull();
        assertThat(image.getLicenseLabel()).isNull();
    }

    @Test
    void knownKoglCodesHaveLabelsAndUnknownFutureLicenseStaysReadable() {
        DestinationImage image = new DestinationImage();
        image.setLicenseType("KOGL_TYPE_1");
        assertThat(image.getLicenseLabel()).isEqualTo("공공누리 제1유형");
        image.setLicenseType("KOGL_TYPE_2");
        assertThat(image.getLicenseLabel()).isEqualTo("공공누리 제2유형");
        image.setLicenseType("KOGL_TYPE_3");
        assertThat(image.getLicenseLabel()).isEqualTo("공공누리 제3유형");
        image.setLicenseType("KOGL_TYPE_4");
        assertThat(image.getLicenseLabel()).isEqualTo("공공누리 제4유형");

        image.setLicenseType("CC_BY_4_0");
        assertThat(image.getLicenseLabel()).isEqualTo("CC_BY_4_0");
    }

    @Test
    void existingKtoPhotographerAloneStillCreatesAttribution() {
        DestinationImage image = new DestinationImage();
        image.setPhotographer("한국관광공사 김지호");

        assertThat(image.isAttributionPresent()).isTrue();
    }

    @Test
    void existingKtoMetadataDisplaysWithoutSourcePageUrlAndKeepsOriginalImageUrlSeparate() {
        DestinationImage image = new DestinationImage();
        image.setSourceName("한국관광공사");
        image.setPhotographer("한국관광공사 김지호");
        image.setLicenseType("KOGL_TYPE_1");
        image.setSourceImageUrl("https://images.example.test/gyeongbokgung.jpg");
        image.setSourceUrl(null);

        assertThat(image.isAttributionPresent()).isTrue();
        assertThat(image.getLicenseLabel()).isEqualTo("공공누리 제1유형");
        assertThat(image.getSafeSourceUrl()).isNull();
        assertThat(image.getSourceImageUrl()).isEqualTo("https://images.example.test/gyeongbokgung.jpg");
    }

    @Test
    void onlyAbsoluteHttpUrlsCanBecomeSourceLinks() {
        DestinationImage image = new DestinationImage();

        image.setSourceUrl("javascript:alert(1)");
        assertThat(image.getSafeSourceUrl()).isNull();
        assertThat(image.isAttributionPresent()).isFalse();

        image.setSourceUrl("https://example.com/photo-source");
        assertThat(image.getSafeSourceUrl()).isEqualTo("https://example.com/photo-source");
        assertThat(image.isAttributionPresent()).isTrue();
    }
}
