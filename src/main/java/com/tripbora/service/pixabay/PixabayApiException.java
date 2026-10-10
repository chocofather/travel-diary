package com.tripbora.service.pixabay;

/**
 * Pixabay API 호출 실패. 메시지는 관리자 화면에 그대로 보여 준다.
 * 요청 URL에 API Key가 들어 있으므로 원인 예외(cause)를 붙이지 않는다(로그에 키가 남지 않게 한다).
 */
public class PixabayApiException extends RuntimeException {

    public enum Reason {
        NOT_CONFIGURED,
        RATE_LIMITED,
        TIMEOUT,
        UPSTREAM
    }

    static final String NOT_CONFIGURED_MESSAGE = "Pixabay API Key가 설정되지 않았습니다.";
    static final String RATE_LIMITED_MESSAGE = "Pixabay 요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.";

    private final Reason reason;

    public PixabayApiException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public static PixabayApiException notConfigured() {
        return new PixabayApiException(Reason.NOT_CONFIGURED, NOT_CONFIGURED_MESSAGE);
    }

    public static PixabayApiException rateLimited() {
        return new PixabayApiException(Reason.RATE_LIMITED, RATE_LIMITED_MESSAGE);
    }

    public Reason reason() {
        return reason;
    }
}
