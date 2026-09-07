package com.example.travlediary.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * 스티커의 표현 스타일. (무엇을 그렸는지가 아니라 어떻게 그렸는지다)
 *
 * <p>분류(category)와는 다른 축이다 — 같은 '여행' 안에 기본 스티커와 실물 느낌 스티커가 함께 있을 수 있다.
 * 마스킹테이프의 갈래(tapeType)와도 별개다. 그쪽은 테이프 필름의 종류일 뿐이다.
 *
 * <p>목록 파일에는 소문자 code 로 적고, 적지 않으면 지금까지 쌓인 스티커처럼 {@link #DEFAULT} 로 본다.
 * 그래서 이미 있는 스티커 줄을 한 줄도 고치지 않아도 된다.
 */
public enum DiaryStickerCollection {

    /** 지금까지의 픽셀·일러스트 스티커. 목록에 collection 을 적지 않으면 이 값이다. */
    DEFAULT("default", "기본"),
    /** 실물 스티커를 붙인 듯한 느낌으로 그린 묶음. */
    REALISTIC("realistic", "리얼");

    /** picker 가 '모든 스타일'을 뜻할 때 쓰는 값. 스티커에는 붙지 않는다. */
    public static final String ALL = "ALL";

    private final String code;
    private final String label;

    DiaryStickerCollection(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 목록 파일과 화면(DOM)이 함께 쓰는 값. */
    public String getCode() {
        return code;
    }

    /** picker 의 하위 필터에 찍는 이름. (다이어리 편집 화면은 한국어 고정이다) */
    public String getLabel() {
        return label;
    }

    /** 적혀 있지 않거나 비어 있으면 기본 묶음으로 본다. 모르는 값은 알아서 넘기지 않는다. */
    public static Optional<DiaryStickerCollection> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.of(DEFAULT);
        }
        String normalized = code.strip().toLowerCase();
        return Arrays.stream(values())
                .filter(collection -> collection.code.equals(normalized))
                .findFirst();
    }
}
