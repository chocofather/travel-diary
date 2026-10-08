package com.tripbora.service.kto;

public class KtoPhotoImportException extends RuntimeException {

    private static final String SAFE_MESSAGE = "관광사진을 저장하지 못했습니다.";

    /** 실패 원인을 구분하는 짧은 설명. 메시지는 그대로 두고 로그·관리 화면 안내에만 쓴다. */
    private final String reason;

    public KtoPhotoImportException() {
        this((String) null);
    }

    public KtoPhotoImportException(String reason) {
        super(SAFE_MESSAGE);
        this.reason = reason;
    }

    protected KtoPhotoImportException(String reason, Throwable cause) {
        super(SAFE_MESSAGE, cause);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
