package com.example.travlediary.service.kto;

public class KtoPhotoApiException extends RuntimeException {

    public enum Kind {
        CONFIGURATION,
        UPSTREAM
    }

    private final Kind kind;

    private KtoPhotoApiException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public static KtoPhotoApiException missingApiKey() {
        return new KtoPhotoApiException(Kind.CONFIGURATION, "관광사진 API 인증키가 설정되지 않았습니다.");
    }

    public static KtoPhotoApiException upstreamFailure() {
        return new KtoPhotoApiException(Kind.UPSTREAM, "관광사진 검색 서비스를 이용할 수 없습니다.");
    }

    public Kind getKind() {
        return kind;
    }
}
