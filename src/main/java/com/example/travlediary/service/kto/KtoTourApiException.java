package com.example.travlediary.service.kto;

public class KtoTourApiException extends RuntimeException {

    public enum Kind {
        CONFIGURATION,
        UPSTREAM
    }

    private final Kind kind;

    private KtoTourApiException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public static KtoTourApiException missingApiKey() {
        return new KtoTourApiException(Kind.CONFIGURATION, "TourAPI 인증키가 설정되지 않았습니다.");
    }

    public static KtoTourApiException upstreamFailure() {
        return new KtoTourApiException(Kind.UPSTREAM, "관광정보를 불러오지 못했습니다.");
    }

    public Kind getKind() {
        return kind;
    }
}
