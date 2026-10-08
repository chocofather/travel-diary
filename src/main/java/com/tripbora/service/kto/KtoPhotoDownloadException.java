package com.tripbora.service.kto;

public class KtoPhotoDownloadException extends RuntimeException {

    private static final String SAFE_MESSAGE = "관광사진을 다운로드하지 못했습니다.";

    /** 실패 원인을 구분하는 짧은 설명(HTTP 상태, 형식, 용량 등). 메시지는 그대로 두고 로그·관리 화면 안내에만 쓴다. */
    private final String reason;

    public KtoPhotoDownloadException() {
        this(null);
    }

    public KtoPhotoDownloadException(String reason) {
        super(SAFE_MESSAGE);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }

}
