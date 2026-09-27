package com.example.travlediary.service.file;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 여행지 카드(메인 추천 카드·공개 여행지 목록 카드)용 썸네일 규칙.
 *
 * <p>카드는 메인에서 약 206×176~362×190, 목록에서 4:3 으로 약 300×225(4열)~490×369(2열)~화면 폭(1열)이다.
 * 원본(최대 수천 px)을 그대로 내려 브라우저가 크게 줄이던 것을, 서버가 미리 줄인 두 가지 크기로 바꾼다.
 * <ul>
 *   <li>{@link Variant#SMALL}: 4:3 상자 480×360 을 채우는 크기 — 데스크톱 1x·2x</li>
 *   <li>{@link Variant#LARGE}: 4:3 상자 960×720 을 채우는 크기 — 태블릿·모바일 2x·3x</li>
 * </ul>
 * 원본 파일과 DB 값은 그대로 두고, 주소만 {@code /uploads/destinations/{파일}} 에서 이끌어 낸다.
 *
 * <p>줄이는 방식(크기·품질)을 바꾸면 {@link #VERSION} 을 올린다. 주소가 바뀌므로 브라우저 캐시와
 * 서버의 이전 썸네일 파일을 모두 새로 쓰게 된다.
 */
public final class DestinationCardThumbnails {

    /**
     * 썸네일 규칙 버전. 주소와 서버 캐시 폴더에 함께 들어간다.
     * v2: 큰 썸네일을 960×720(4:3) 상자 기준으로 바꾸고, 줄인 결과를 언제나 쓰게 해 srcset 폭과 실제 픽셀을 맞췄다.
     */
    public static final String VERSION = "v2";

    static final String URL_PREFIX = "/thumbnails/destinations/";

    /** 여행지 업로드 폴더의 JPEG/PNG 파일 이름만 받는다. 경로 구분자·상위 폴더 표기는 들어올 수 없다. */
    private static final Pattern FILE_NAME = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._-]{0,200}\\.(?:jpe?g|png)", Pattern.CASE_INSENSITIVE);

    private static final Pattern ORIGINAL_URL = Pattern.compile(
            "/uploads/destinations/(" + FILE_NAME.pattern() + ")", Pattern.CASE_INSENSITIVE);

    /**
     * 썸네일 크기. 가로·세로가 모두 이 상자 이상이 되도록 줄인다(object-fit: cover 로 잘라 보이므로).
     * 원본이 이미 상자 안쪽이면 원본을 그대로 쓴다. 키우지 않는다.
     */
    enum Variant {
        SMALL(480, 360),
        LARGE(960, 720);

        final int width;
        final int height;

        Variant(int width, int height) {
            this.width = width;
            this.height = height;
        }

        static Optional<Variant> ofWidth(int width) {
            return Arrays.stream(values()).filter(variant -> variant.width == width).findFirst();
        }

        /**
         * 바로 선 원본 치수로 이 썸네일의 실제 가로 픽셀을 셈한다.
         * {@link RasterImageResizer#coverThumbnail} 과 같은 식이라 만든 파일의 폭과 같다.
         */
        int servedWidth(int uprightWidth, int uprightHeight) {
            double scale = Math.max((double) width / uprightWidth, (double) height / uprightHeight);
            return scale >= 1 ? uprightWidth : Math.max(1, (int) Math.round(uprightWidth * scale));
        }
    }

    private DestinationCardThumbnails() {
    }

    static boolean isFileName(String fileName) {
        return fileName != null && FILE_NAME.matcher(fileName).matches();
    }

    /** 여행지 업로드 원본 주소에서 파일 이름만 꺼낸다. 다른 주소(외부 URL·기본 이미지 등)는 empty. */
    static Optional<String> fileName(String imageUrl) {
        if (imageUrl == null) {
            return Optional.empty();
        }
        Matcher matcher = ORIGINAL_URL.matcher(imageUrl.trim());
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    static String url(Variant variant, String fileName) {
        return URL_PREFIX + VERSION + "/" + variant.width + "/" + fileName;
    }
}
