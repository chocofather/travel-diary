package com.example.travlediary.service.kto;

/**
 * 외국어 TourAPI 호출 실패.
 *
 * <p>화면에 그대로 보여 주는 문구라 어느 언어를 부르다 실패했는지 밝힌다.
 */
public class KtoForeignTourApiException extends RuntimeException {

    public enum Kind {
        CONFIGURATION,
        UPSTREAM
    }

    private final Kind kind;

    private KtoForeignTourApiException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public static KtoForeignTourApiException missingApiKey(KtoForeignLanguage language) {
        return new KtoForeignTourApiException(Kind.CONFIGURATION,
                language.getLabel() + " TourAPI 인증키가 설정되지 않았습니다.");
    }

    public static KtoForeignTourApiException upstreamFailure(KtoForeignLanguage language) {
        return new KtoForeignTourApiException(Kind.UPSTREAM,
                language.getLabel() + " 관광정보를 불러오지 못했습니다.");
    }

    public Kind getKind() {
        return kind;
    }
}
