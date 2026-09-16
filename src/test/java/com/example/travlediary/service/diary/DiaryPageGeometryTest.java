package com.example.travlediary.service.diary;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DiaryPageGeometryTest {

    @Test
    void pageAspectIsRealA5Portrait() {
        assertThat(DiaryPageGeometry.PAGE_CANVAS_ASPECT)
                .isCloseTo(148.0 / 210.0, within(0.0000001));
        assertThat(DiaryPageGeometry.REFERENCE_WIDTH_PX).isEqualTo(576);
        assertThat(DiaryPageGeometry.referenceHeightPx())
                .isCloseTo(817.2973, within(0.0001));
    }

    @Test
    void legacyVerticalCoordinatesKeepTheirScreenPixelsOnA5() {
        assertThat(DiaryPageGeometry.LEGACY_VERTICAL_SCALE)
                .isEqualByComparingTo("0.6531939605");
        assertThat(DiaryPageGeometry.fromLegacyVertical(new BigDecimal("0.41000")))
                .isEqualByComparingTo("0.26781");
        assertThat(DiaryPageGeometry.fromLegacyVertical(new BigDecimal("0.18000")))
                .isEqualByComparingTo("0.11757");
        assertThat(DiaryPageGeometry.fromLegacyVertical(new BigDecimal("0.09000")))
                .isEqualByComparingTo("0.05879");
    }

    @Test
    void horizontalCoordinatesAndRotationNeedNoConversion() {
        BigDecimal horizontal = new BigDecimal("0.42000");

        assertThat(DiaryPageGeometry.fromLegacyHorizontal(horizontal)).isSameAs(horizontal);
    }
}
