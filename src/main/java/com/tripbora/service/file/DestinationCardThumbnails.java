package com.tripbora.service.file;

import com.tripbora.service.destination.DestinationImageService;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    /**
     * 캐시 폴더에 남아 있을 수 있는 규칙 버전. 앞의 것은 더 이상 만들지 않는 예전 규칙이다.
     * 새로 만드는 것은 {@link #VERSION} 뿐이고, 정리 작업이 예전 파일까지 찾을 때 이 목록을 쓴다.
     */
    static final List<String> CACHE_VERSIONS = List.of("v1", VERSION);

    static final String URL_PREFIX = "/thumbnails/destinations/";

    /** 업로드 폴더 안 여행지 원본 폴더. */
    static final String ORIGINAL_DIRECTORY = "destinations";
    /** 업로드 폴더 안 여행지 카드 썸네일 캐시 폴더. 공개 정적 매핑 목록에 없고, 지워도 다시 만들어진다. */
    static final String CACHE_DIRECTORY = "thumbnail-cache/destinations";

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

        int width() {
            return width;
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

    /** 파일 이름에서 DB 에 저장된 원본 주소({@code /uploads/destinations/{파일}})를 되돌린다. */
    static String originalUrl(String fileName) {
        return "/uploads/" + ORIGINAL_DIRECTORY + "/" + fileName;
    }

    /** 한 번에 라이선스를 확인할 파일 수. 폴더 전체를 나눠 묻는다. */
    static final int LICENSE_LOOKUP_BATCH = 500;

    /**
     * 주어진 파일 중 공공누리 제3유형(변경금지) 원본의 파일 이름.
     * 판정은 {@link DestinationImageService#noDerivativeImageUrls} 의 공통 규칙(출처 행 우선)을 그대로 쓴다.
     *
     * @throws RuntimeException 라이선스를 확인하지 못했을 때. 부르는 쪽이 추정하지 않고 멈춘다.
     */
    static Set<String> noDerivativeFileNames(DestinationImageService licenses, List<String> fileNames) {
        Set<String> result = new HashSet<>();
        for (int start = 0; start < fileNames.size(); start += LICENSE_LOOKUP_BATCH) {
            List<String> urls = fileNames.subList(start, Math.min(fileNames.size(), start + LICENSE_LOOKUP_BATCH))
                    .stream()
                    .map(DestinationCardThumbnails::originalUrl)
                    .toList();
            licenses.noDerivativeImageUrls(urls).forEach(url -> fileName(url).ifPresent(result::add));
        }
        return result;
    }

    /** 업로드 폴더 안 여행지 원본 폴더 위치. */
    static Path originalDirectory(Path uploadRoot) {
        return uploadRoot.resolve(ORIGINAL_DIRECTORY).normalize();
    }

    /** 이 규칙 버전·폭의 썸네일 캐시 폴더 위치({@code thumbnail-cache/destinations/{버전}/{폭}}). */
    static Path cacheDirectory(Path uploadRoot, String version, int width) {
        return uploadRoot.resolve(CACHE_DIRECTORY)
                .resolve(version)
                .resolve(String.valueOf(width))
                .normalize();
    }

    static String url(Variant variant, String fileName) {
        return URL_PREFIX + VERSION + "/" + variant.width + "/" + fileName;
    }
}
