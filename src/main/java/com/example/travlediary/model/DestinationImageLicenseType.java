package com.example.travlediary.model;

import java.util.Arrays;

/**
 * 관리자 화면에서 자주 쓰는 여행지 이미지 라이선스 선택지.
 *
 * <p>DB 컬럼은 문자열로 유지한다. 따라서 이 목록에 없는 해외 라이선스나 향후 코드도
 * 조회·표시할 수 있고, 이 enum은 입력 편의를 위한 알려진 값 목록으로만 사용한다.</p>
 */
public enum DestinationImageLicenseType {
    KOGL_TYPE_1("공공누리 제1유형"),
    KOGL_TYPE_2("공공누리 제2유형"),
    KOGL_TYPE_3("공공누리 제3유형"),
    KOGL_TYPE_4("공공누리 제4유형"),
    OTHER("기타"),
    NONE("별도 표기 없음");

    private final String displayName;

    DestinationImageLicenseType(String displayName) {
        this.displayName = displayName;
    }

    public String getCode() {
        return name();
    }

    public String getDisplayName() {
        return displayName;
    }

    public static String displayName(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.strip();
        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalized))
                .map(DestinationImageLicenseType::getDisplayName)
                .findFirst()
                .orElse(normalized);
    }
}
