package com.example.travlediary.service.diary;

import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Iterator;

/**
 * 폴라로이드로 붙일 사진의 처음 크기.
 *
 * <p>폴라로이드는 흰 프레임 안쪽에 사진이 꽉 차는 모습이다. 그래서 요소 상자의 비율이
 * 사진 비율과 맞지 않으면 남는 자리가 흰 여백으로 보이거나 사진이 잘린다.
 * 가로 사진에는 가로 폴라로이드, 세로 사진에는 세로 폴라로이드가 되도록
 * 사진의 원본 비율에서 요소 높이를 거꾸로 구한다.
 *
 * <p>프레임 두께는 화면(diary.css) 쪽 규칙과 같은 값이다. 좌·우·위는 얇고 아래만 조금 넓다.
 * 두 값이 어긋나면 사진 자리가 프레임과 맞지 않으므로 한 곳에서만 바꾼다.
 *
 * <p>페이지 다꾸와 표지 디자인이 같은 셈을 함께 쓴다. 다른 것은 캔버스 비율뿐이라
 * 같은 사진이면 두 화면에서 같은 폴라로이드 모습이 된다.
 */
public final class DiaryPhotoFrame {

    /** 좌·우·위 흰 여백. 요소 폭 기준이다. (diary.css 의 padding 과 같은 값) */
    private static final double SIDE = 0.035;
    /** 아래 흰 여백. 위쪽의 두 배 넘게 넓은 것이 폴라로이드다운 비대칭이다. */
    private static final double BOTTOM = 0.08;
    /** 프레임을 뺀 사진 자리의 폭 / 높이 (요소 폭 기준) */
    public static final double INNER_WIDTH = 1 - 2 * SIDE;
    public static final double FRAME_HEIGHT = SIDE + BOTTOM;

    /** 종이 한 장의 가로/세로 비율. (읽기/편집이 같은 비율을 쓴다) */
    public static final double PAGE_CANVAS_ASPECT = 41.0 / 38.0;
    /** 표지 한 장의 가로/세로 비율. */
    public static final double COVER_CANVAS_ASPECT = 3.0 / 4.0;

    /** 너무 길쭉한 사진이 상자를 캔버스 밖까지 밀어내지 않게 둔다. */
    private static final double RATIO_MIN = 0.2;
    private static final double RATIO_MAX = 5.0;
    /** 요소 높이 상한. (DB CHECK 과 같은 1 보다 조금 낮게 두어 화면에 다 들어오게 한다) */
    private static final double HEIGHT_MAX = 0.9;

    private DiaryPhotoFrame() {
    }

    /**
     * 폴라로이드 요소의 처음 크기. 0~1 상대값 {너비, 높이} 를 돌려준다.
     *
     * <p>사진 자리가 원본 비율 그대로가 되도록 높이를 구한다.
     * 높이가 캔버스를 넘으면 같은 비율을 지킨 채 너비를 함께 줄인다.
     *
     * @param photoRatio   사진 원본의 가로/세로. 알 수 없으면 0 이하를 넘기면 된다(정사각으로 본다).
     * @param canvasAspect 붙일 캔버스의 가로/세로.
     * @param width        고르지 않았을 때 쓰는 기본 너비.
     */
    public static BigDecimal[] polaroidSize(double photoRatio, double canvasAspect,
                                            BigDecimal width) {
        double ratio = photoRatio > 0 ? Math.min(Math.max(photoRatio, RATIO_MIN), RATIO_MAX) : 1.0;
        double elementWidth = width.doubleValue();
        /*
          사진 자리의 높이 = 사진 자리의 폭 / 사진 비율.
          거기에 위아래 프레임을 더한 값이 요소의 높이다. (둘 다 요소 폭 기준으로 센다)
          캔버스의 가로/세로가 다르므로 상대값으로 옮길 때 그 비율을 함께 곱한다.
        */
        double elementHeight = elementWidth * canvasAspect * (INNER_WIDTH / ratio + FRAME_HEIGHT);
        if (elementHeight > HEIGHT_MAX) {
            elementWidth = elementWidth * HEIGHT_MAX / elementHeight;
            elementHeight = HEIGHT_MAX;
        }
        return new BigDecimal[]{relative(elementWidth), relative(elementHeight)};
    }

    /**
     * 올린 사진의 가로/세로. 읽을 수 없으면 0 을 돌려준다. (그때는 정사각으로 본다)
     *
     * <p>그림 전체를 펼치지 않고 머리말만 읽어 크기를 얻는다.
     */
    public static double ratioOf(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return 0;
        }
        try (InputStream stream = image.getInputStream();
             ImageInputStream source = ImageIO.createImageInputStream(stream)) {
            if (source == null) {
                return 0;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(source);
            if (!readers.hasNext()) {
                return 0;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(source);
                int width = reader.getWidth(reader.getMinIndex());
                int height = reader.getHeight(reader.getMinIndex());
                return height > 0 ? (double) width / height : 0;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ignored) {
            // 크기를 못 읽는 것은 실패가 아니다. 붙이는 것 자체는 그대로 이어진다.
            return 0;
        }
    }

    private static BigDecimal relative(double value) {
        return BigDecimal.valueOf(value).setScale(5, RoundingMode.HALF_UP);
    }
}
