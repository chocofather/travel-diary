package com.example.travlediary.service.diary;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 일반 사진(FULL)의 처음 크기.
 *
 * <p>버그: 상자를 늘 정사각 상대값으로 잡아 두었다. 좌표가 0~1 상대값이라 캔버스의
 * 가로/세로가 그대로 곱해지므로, 세로로 긴 표지에서는 그 상자가 세로가 되어
 * 가로 사진의 좌우가 잘렸다. 이제 화면에서 보이는 비율이 원본과 같아지도록 센다.
 *
 * <p>여기에서 재는 것은 상대값 자체가 아니라 "화면에서 보이는 비율" 이다.
 */
class DiaryPhotoFrameFullSizeTest {

    private static final BigDecimal BASE = new BigDecimal("0.34000");

    /** 상대값 크기를 실제 화면 비율(가로/세로)로 바꾼다. 이것이 원본 비율과 같아야 한다. */
    private double screenRatio(BigDecimal[] size, double canvasAspect) {
        return size[0].doubleValue() * canvasAspect / size[1].doubleValue();
    }

    /** 1) 가로 사진은 화면에서도 가로로 붙는다. */
    @Test
    void aLandscapePhotoBecomesALandscapeElement() {
        double ratio = 1600.0 / 900.0;

        for (double canvas : new double[]{
                DiaryPhotoFrame.PAGE_CANVAS_ASPECT, DiaryPhotoFrame.COVER_CANVAS_ASPECT}) {
            BigDecimal[] size = DiaryPhotoFrame.fullSize(ratio, canvas, BASE);

            assertThat(screenRatio(size, canvas)).isCloseTo(ratio, within(0.001));
            assertThat(screenRatio(size, canvas)).isGreaterThan(1.0);
            // 가로 사진은 너비가 기본 크기다.
            assertThat(size[0]).isEqualByComparingTo(BASE);
            assertThat(size[1]).isLessThan(BASE);
        }
    }

    /** 2) 세로 사진은 화면에서도 세로로 붙는다. */
    @Test
    void aPortraitPhotoBecomesAPortraitElement() {
        double ratio = 900.0 / 1200.0;

        for (double canvas : new double[]{
                DiaryPhotoFrame.PAGE_CANVAS_ASPECT, DiaryPhotoFrame.COVER_CANVAS_ASPECT}) {
            BigDecimal[] size = DiaryPhotoFrame.fullSize(ratio, canvas, BASE);

            assertThat(screenRatio(size, canvas)).isCloseTo(ratio, within(0.001));
            assertThat(screenRatio(size, canvas)).isLessThan(1.0);
            /*
              긴 쪽의 화면 크기가 기본 크기를 넘지 않는다. A5처럼 사진보다 더 세로로 긴
              캔버스에서는 상대 너비가 기준이 될 수 있으므로 정규화 값의 대소로 방향을
              판정하지 않고 위의 실제 화면 비율로 판정한다.
            */
            assertThat(size[0]).isLessThanOrEqualTo(BASE);
            assertThat(size[1]).isLessThanOrEqualTo(BASE);
            assertThat(size[0].compareTo(BASE) == 0 || size[1].compareTo(BASE) == 0).isTrue();
        }
    }

    /** 3) 정사각 사진은 화면에서도 정사각으로 붙는다. */
    @Test
    void aSquarePhotoLooksSquareOnScreen() {
        for (double canvas : new double[]{
                DiaryPhotoFrame.PAGE_CANVAS_ASPECT, DiaryPhotoFrame.COVER_CANVAS_ASPECT}) {
            BigDecimal[] size = DiaryPhotoFrame.fullSize(1.0, canvas, BASE);

            assertThat(screenRatio(size, canvas)).isCloseTo(1.0, within(0.001));
        }
    }

    /**
     * 4) 같은 A5 캔버스를 쓰는 페이지와 표지는 같은 상대 크기를 얻는다.
     */
    @Test
    void pageAndCoverUseTheSameStoredSizeAtTheSameA5Aspect() {
        double ratio = 1600.0 / 900.0;

        BigDecimal[] page = DiaryPhotoFrame.fullSize(
                ratio, DiaryPhotoFrame.PAGE_CANVAS_ASPECT, BASE);
        BigDecimal[] cover = DiaryPhotoFrame.fullSize(
                ratio, DiaryPhotoFrame.COVER_CANVAS_ASPECT, BASE);

        assertThat(page[0]).isEqualByComparingTo(cover[0]);
        assertThat(page[1]).isEqualByComparingTo(cover[1]);
        assertThat(screenRatio(page, DiaryPhotoFrame.PAGE_CANVAS_ASPECT))
                .isCloseTo(screenRatio(cover, DiaryPhotoFrame.COVER_CANVAS_ASPECT),
                        within(0.001));
    }

    /**
     * 고친 자리를 못박아 둔다. 예전에는 표지에서 가로 사진이 세로 상자가 됐다.
     * (0.34 × 0.34 상대값 → 화면 3:4 세로)
     */
    @Test
    void aLandscapePhotoIsNoLongerPutIntoAPortraitBoxOnTheCover() {
        BigDecimal[] size = DiaryPhotoFrame.fullSize(
                1600.0 / 900.0, DiaryPhotoFrame.COVER_CANVAS_ASPECT, BASE);

        // 예전 값(정사각 상대값)이었다면 A5 캔버스 비율만큼 세로였다.
        assertThat(size[0]).isNotEqualByComparingTo(size[1]);
        assertThat(screenRatio(size, DiaryPhotoFrame.COVER_CANVAS_ASPECT))
                .isNotCloseTo(DiaryPhotoFrame.COVER_CANVAS_ASPECT, within(0.01));
    }

    /** 크기를 읽지 못한 사진은 정사각으로 본다. 붙이는 것 자체는 이어진다. */
    @Test
    void anUnknownRatioFallsBackToASquare() {
        BigDecimal[] size = DiaryPhotoFrame.fullSize(
                0, DiaryPhotoFrame.COVER_CANVAS_ASPECT, BASE);

        assertThat(screenRatio(size, DiaryPhotoFrame.COVER_CANVAS_ASPECT))
                .isCloseTo(1.0, within(0.001));
    }

    /** 아주 길쭉한 사진도 처음부터 캔버스를 넘지 않고, 저장 조건(0 초과)을 지킨다. */
    @Test
    void extremeRatiosStayInsideTheCanvas() {
        for (double ratio : new double[]{0.02, 0.2, 5.0, 50.0}) {
            for (double canvas : new double[]{
                    DiaryPhotoFrame.PAGE_CANVAS_ASPECT, DiaryPhotoFrame.COVER_CANVAS_ASPECT}) {
                BigDecimal[] size = DiaryPhotoFrame.fullSize(ratio, canvas, BASE);

                assertThat(size[0].doubleValue()).isGreaterThan(0).isLessThanOrEqualTo(1.0);
                assertThat(size[1].doubleValue()).isGreaterThan(0).isLessThanOrEqualTo(1.0);
                // 긴 쪽이 기본 크기를 넘지 않는다.
                assertThat(size[0]).isLessThanOrEqualTo(BASE);
                assertThat(size[1]).isLessThanOrEqualTo(BASE);
            }
        }
    }

    /** 7) 폴라로이드는 예전 셈 그대로다. 프레임을 포함한 상자라 FULL 과 값이 다르다. */
    @Test
    void thePolaroidSizingIsUnchanged() {
        double ratio = 1600.0 / 900.0;
        BigDecimal[] polaroid = DiaryPhotoFrame.polaroidSize(
                ratio, DiaryPhotoFrame.COVER_CANVAS_ASPECT, BASE);

        // 예전 공식 그대로: 높이 = 폭 × 캔버스비율 × (사진자리폭 / 비율 + 프레임높이)
        double expectedHeight = BASE.doubleValue() * DiaryPhotoFrame.COVER_CANVAS_ASPECT
                * (DiaryPhotoFrame.INNER_WIDTH / ratio + DiaryPhotoFrame.FRAME_HEIGHT);
        assertThat(polaroid[0]).isEqualByComparingTo(BASE);
        assertThat(polaroid[1].doubleValue()).isCloseTo(expectedHeight, within(0.00001));

        // 프레임이 있어 FULL 보다 세로로 길다. 두 모습이 섞이지 않았다는 확인이다.
        BigDecimal[] full = DiaryPhotoFrame.fullSize(
                ratio, DiaryPhotoFrame.COVER_CANVAS_ASPECT, BASE);
        assertThat(polaroid[1]).isGreaterThan(full[1]);
    }
}
