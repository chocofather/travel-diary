package com.example.travlediary.service.inquiry;

/**
 * 1:1 문의 입력값 오류.
 *
 * <p>공개 화면은 언어에 맞는 문구를 보여 줘야 하므로 메시지 코드를 함께 들고 다닌다.
 * 코드가 없거나 번들에 없으면 여기 담긴 한국어 문구가 그대로 쓰인다.
 * (관리자 화면은 한국어 고정이라 지금처럼 문구만 써도 된다)
 */
public class InquiryValidationException extends RuntimeException {
    private final String field;
    private final String messageCode;
    private final Object[] messageArgs;

    public InquiryValidationException(String field, String message) {
        this(field, null, message);
    }

    public InquiryValidationException(String field, String messageCode, String message,
                                      Object... messageArgs) {
        super(message);
        this.field = field;
        this.messageCode = messageCode;
        this.messageArgs = messageArgs;
    }

    public String getField() {
        return field;
    }

    public String getMessageCode() {
        return messageCode;
    }

    public Object[] getMessageArgs() {
        return messageArgs == null ? new Object[0] : messageArgs.clone();
    }
}
