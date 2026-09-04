package com.example.travlediary.service.kto;

import java.util.Arrays;
import java.util.Optional;

/**
 * TourAPI 외국어 서비스가 있는 언어.
 *
 * <p>화면 번역 슬롯과 같은 canonical 코드를 쓴다. 국문(KorService2)은 원본이라 여기 없고,
 * 목록에 없는 locale 은 임의로 다른 언어로 대신하지 않는다.
 */
public enum KtoForeignLanguage {

    ENGLISH("en", "영문"),
    JAPANESE("ja", "일문"),
    CHINESE_SIMPLIFIED("zh-CN", "중문 간체"),
    CHINESE_TRADITIONAL("zh-TW", "중문 번체");

    private final String languageTag;
    /** 오류 문구에 쓰는 이름. */
    private final String label;

    KtoForeignLanguage(String languageTag, String label) {
        this.languageTag = languageTag;
        this.label = label;
    }

    public String getLanguageTag() {
        return languageTag;
    }

    public String getLabel() {
        return label;
    }

    /** 지원하는 언어일 때만 값을 준다. 비슷한 언어로 대신하지 않는다. */
    public static Optional<KtoForeignLanguage> fromLanguageTag(String languageTag) {
        if (languageTag == null || languageTag.isBlank()) {
            return Optional.empty();
        }
        String candidate = languageTag.strip();
        return Arrays.stream(values())
                .filter(language -> language.languageTag.equals(candidate))
                .findFirst();
    }
}
