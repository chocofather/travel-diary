package com.example.travlediary.service.kto;

public class KtoEnglishTourApiException extends RuntimeException {

    public enum Kind {
        CONFIGURATION,
        UPSTREAM
    }

    private final Kind kind;

    private KtoEnglishTourApiException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public static KtoEnglishTourApiException missingApiKey() {
        return new KtoEnglishTourApiException(
                Kind.CONFIGURATION, "영문 TourAPI 인증키가 설정되지 않았습니다.");
    }

    public static KtoEnglishTourApiException upstreamFailure() {
        return new KtoEnglishTourApiException(
                Kind.UPSTREAM, "영문 관광정보를 불러오지 못했습니다.");
    }

    public Kind getKind() {
        return kind;
    }
}
