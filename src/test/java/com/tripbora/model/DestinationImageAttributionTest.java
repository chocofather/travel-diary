package com.tripbora.model;

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
        assertThat(image.isNoDerivatives()).isFalse();
        image.setLicenseType("KOGL_TYPE_2");
        assertThat(image.getLicenseLabel()).isEqualTo("공공누리 제2유형");
        image.setLicenseType("KOGL_TYPE_3");
        assertThat(image.getLicenseLabel()).isEqualTo("공공누리 제3유형 · 변경금지");
        assertThat(image.isNoDerivatives()).isTrue();
        image.setLicenseType("KOGL_TYPE_4");
        assertThat(image.getLicenseLabel()).isEqualTo("공공누리 제4유형");

        image.setLicenseType("CC_BY_4_0");
        assertThat(image.getLicenseLabel()).isEqualTo("CC_BY_4_0");
    }

    @Test
    void licenseChoicesKeepLegacyValuesAndPreferDetailForDisplay() {
        DestinationImage image = new DestinationImage();

        image.setLicenseType("NONE");
        assertThat(image.getLicenseLabel()).isEqualTo("별도 표기 없음");
        image.setLicenseType("OTHER");
        assertThat(image.getLicenseLabel()).isEqualTo("기타 라이선스");
        image.setLicenseType("DIRECT");
        assertThat(image.getLicenseLabel()).isEqualTo("직접 촬영 / 자체 저작권");
        image.setLicenseType("UNKNOWN");
        assertThat(image.getLicenseLabel()).isEqualTo("라이선스 확인 안 됨");

        image.setLicenseType("CREATIVE_COMMONS");
        image.setLicenseDetail("CC BY 4.0");
        assertThat(image.getLicenseDisplay()).isEqualTo("CC BY 4.0");

        image.setLicenseType("PERMISSION");
        image.setLicenseDetail(null);
        assertThat(image.getLicenseDisplay()).isEqualTo("별도 이용허락");

        image.setLicenseDetail("한국관광공사 별도 사용 허락");
        assertThat(image.getLicenseDisplay()).isEqualTo("한국관광공사 별도 사용 허락");
        image.setLicenseType("OTHER");
        image.setLicenseDetail("서울특별시 공공저작물 이용조건");
        assertThat(image.getLicenseDisplay()).isEqualTo("서울특별시 공공저작물 이용조건");
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

    /** 관리 화면 카드의 출처 상태와 한 줄 요약. 출처 입력폼을 접어도 이 두 가지로 사진을 훑어본다. */
    @Test
    void cardSourceStatusAndSummaryFollowTheSavedSource() {
        DestinationImage image = new DestinationImage();
        assertThat(image.getSourceStatus()).isEqualTo("MISSING");
        assertThat(image.getSourceSummary()).isNull();

        // URL 만 있고 제공기관·촬영자가 없으면 아직 출처 입력이 필요하다.
        image.setSourceUrl("https://example.com/photo-source");
        image.setPhotographer("  ");
        assertThat(image.getSourceStatus()).isEqualTo("MISSING");

        image.setSourceName("한국관광공사");
        assertThat(image.getSourceStatus()).isEqualTo("LICENSE_NEEDED");
        image.setLicenseType("UNKNOWN");
        assertThat(image.getSourceStatus()).isEqualTo("LICENSE_NEEDED");

        image.setPhotographer("김지호");
        image.setLicenseType("KOGL_TYPE_1");
        assertThat(image.getSourceStatus()).isEqualTo("COMPLETE");
        assertThat(image.getSourceSummary()).isEqualTo("한국관광공사 · 김지호 · 공공누리 제1유형");

        image.setSourceType(DestinationImage.COMMONS_SOURCE_TYPE);
        assertThat(image.getSourceStatus()).isEqualTo("COMMONS");
    }
}
