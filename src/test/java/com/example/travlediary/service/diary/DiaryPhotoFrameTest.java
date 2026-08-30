package com.example.travlediary.service.diary;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 폴라로이드의 처음 크기. 사진 자리가 원본 비율 그대로가 되도록 요소 높이를 구한다.
 * (프레임 두께는 diary.css 의 padding 과 같은 값을 쓴다)
 */
class DiaryPhotoFrameTest {

    private static final BigDecimal WIDTH = new BigDecimal("0.34000");

    /** 사진 자리의 가로/세로가 원본과 같아야 사진이 잘리지도, 흰 자리가 남지도 않는다. */
    @Test
    void thePhotoAreaKeepsTheOriginalRatio() {
        for (double ratio : new double[]{4.0 / 3, 3.0 / 4, 1.0, 16.0 / 9}) {
            BigDecimal[] size = DiaryPhotoFrame.polaroidSize(
                    ratio, DiaryPhotoFrame.PAGE_CANVAS_ASPECT, WIDTH);

            assertThat(photoAreaRatio(size, DiaryPhotoFrame.PAGE_CANVAS_ASPECT))
                    .as("%s", ratio).isCloseTo(ratio, org.assertj.core.data.Offset.offset(0.02));
        }
    }

    /** 가로 사진은 가로 폴라로이드, 세로 사진은 세로 폴라로이드가 된다. */
    @Test
    void aWidePhotoBecomesAWideFrameAndATallOneATallFrame() {
        BigDecimal[] wide = DiaryPhotoFrame.polaroidSize(
                16.0 / 9, DiaryPhotoFrame.COVER_CANVAS_ASPECT, WIDTH);
        BigDecimal[] tall = DiaryPhotoFrame.polaroidSize(
                3.0 / 4, DiaryPhotoFrame.COVER_CANVAS_ASPECT, WIDTH);

        // 표지는 세로형 캔버스라 상대값을 실제 모습으로 옮겨 견준다
        assertThat(shape(wide, DiaryPhotoFrame.COVER_CANVAS_ASPECT)).isGreaterThan(1.0);
        assertThat(shape(tall, DiaryPhotoFrame.COVER_CANVAS_ASPECT)).isLessThan(1.0);
    }

    /** 아래 여백만 넓다. 위·좌·우는 얇고 서로 비슷하다. */
    @Test
    void onlyTheBottomMarginIsWider() {
        BigDecimal[] size = DiaryPhotoFrame.polaroidSize(
                1.0, DiaryPhotoFrame.PAGE_CANVAS_ASPECT, WIDTH);

        // 요소 높이 = 사진 높이 + 위·아래 프레임. 정사각 사진이면 사진 높이 = 사진 폭이다
        double width = actualWidth(size, DiaryPhotoFrame.PAGE_CANVAS_ASPECT);
        assertThat(size[1].doubleValue() / width)
                .isCloseTo(DiaryPhotoFrame.INNER_WIDTH + DiaryPhotoFrame.FRAME_HEIGHT,
                        org.assertj.core.data.Offset.offset(0.01));

        // 아래 여백은 위쪽의 두 배 남짓 넓다. (폴라로이드다운 비대칭)
        double side = (1 - DiaryPhotoFrame.INNER_WIDTH) / 2;
        double bottom = DiaryPhotoFrame.FRAME_HEIGHT - side;
        assertThat(bottom / side).isBetween(1.7, 2.5);
    }

    /** 아주 길쭉한 사진도 캔버스를 넘지 않는다. 대신 폭이 함께 줄어든다. */
    @Test
    void aVeryTallPhotoShrinksInsteadOfOverflowing() {
        BigDecimal[] size = DiaryPhotoFrame.polaroidSize(
                0.1, DiaryPhotoFrame.COVER_CANVAS_ASPECT, WIDTH);

        assertThat(size[1].doubleValue()).isLessThanOrEqualTo(1.0);
        assertThat(size[0].doubleValue()).isLessThan(WIDTH.doubleValue());
    }

    /** 크기를 못 읽은 사진은 정사각으로 본다. (붙이는 것 자체는 그대로 이어진다) */
    @Test
    void anUnknownRatioIsTreatedAsASquare() {
        assertThat(DiaryPhotoFrame.polaroidSize(0, DiaryPhotoFrame.PAGE_CANVAS_ASPECT, WIDTH))
                .isEqualTo(DiaryPhotoFrame.polaroidSize(
                        1.0, DiaryPhotoFrame.PAGE_CANVAS_ASPECT, WIDTH));
    }

    /*
      상대값을 실제 모습으로 옮긴다.
      캔버스를 가로 = 비율, 세로 = 1 로 보면 요소의 실제 폭은 w * 비율, 높이는 h 다.
    */
    private double actualWidth(BigDecimal[] size, double canvasAspect) {
        return size[0].doubleValue() * canvasAspect;
    }

    /** 요소 안에서 프레임을 뺀 사진 자리의 가로/세로. */
    private double photoAreaRatio(BigDecimal[] size, double canvasAspect) {
        double width = actualWidth(size, canvasAspect);
        return (width * DiaryPhotoFrame.INNER_WIDTH)
                / (size[1].doubleValue() - width * DiaryPhotoFrame.FRAME_HEIGHT);
    }

    /** 요소 자체의 가로/세로. (1 보다 크면 가로형이다) */
    private double shape(BigDecimal[] size, double canvasAspect) {
        return actualWidth(size, canvasAspect) / size[1].doubleValue();
    }
}
