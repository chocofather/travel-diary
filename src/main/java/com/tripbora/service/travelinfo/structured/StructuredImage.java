package com.tripbora.service.travelinfo.structured;

import java.util.regex.Pattern;

/**
 * 구조화 콘텐츠 이미지 한 장. 이미지는 언어와 상관없이 함께 쓴다. (alt·캡션 같은 글은 블록 쪽에 둔다)
 *
 * <p>width / height 는 업로드 때 서버가 읽어 둔 실제 크기다. 공개 화면이 자리를 미리 잡아
 * 화면이 밀리지 않게 하는 데 쓴다.
 *
 * @param url {@value #URL_PREFIX} 아래 서버가 만든 파일 경로만 받는다
 */
public record StructuredImage(String url, Integer width, Integer height) {

    /** 구조화 콘텐츠 이미지 전용 업로드 경로. */
    public static final String URL_PREFIX = "/uploads/travel-info/content/";

    /**
     * 서버가 저장하는 이름(UUID + 판별한 확장자)만 허용한다.
     * 외부 주소, 상대 경로(../), 쿼리·인코딩 문자, 다른 업로드 폴더는 모두 여기서 걸러진다.
     */
    static final Pattern URL_PATTERN = Pattern.compile(
            "^" + Pattern.quote(URL_PREFIX)
                    + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                    + "\\.(?:jpg|png|webp)$");
}
