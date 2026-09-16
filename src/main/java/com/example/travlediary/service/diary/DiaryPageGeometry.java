package com.example.travlediary.service.diary;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 다이어리 내지의 A5 좌표계와 기존 41:38 좌표 변환. */
public final class DiaryPageGeometry {

    public static final int REFERENCE_WIDTH_PX = 576;
    public static final double PAGE_CANVAS_ASPECT = 148.0 / 210.0;

    /** (기존 높이/폭) / (A5 높이/폭) = (38/41) / (210/148). */
    public static final BigDecimal LEGACY_VERTICAL_SCALE =
            new BigDecimal("2812").divide(new BigDecimal("4305"), 10, RoundingMode.HALF_UP);

    private DiaryPageGeometry() {
    }

    public static double referenceHeightPx() {
        return REFERENCE_WIDTH_PX * 210.0 / 148.0;
    }

    /** 기존 종이에서의 세로 픽셀 위치/크기를 A5 상대값으로 옮긴다. */
    public static BigDecimal fromLegacyVertical(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.multiply(LEGACY_VERTICAL_SCALE).setScale(5, RoundingMode.HALF_UP);
    }

    /** 기준 폭이 같으므로 가로 상대값은 변환하지 않는다. */
    public static BigDecimal fromLegacyHorizontal(BigDecimal value) {
        return value;
    }
}
