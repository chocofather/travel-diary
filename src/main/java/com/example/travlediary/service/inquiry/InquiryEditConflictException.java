package com.example.travlediary.service.inquiry;

/**
 * 수정할 수 없는 상태의 1:1 문의를 고치려 할 때 던진다.
 *
 * <p>공개 화면 안내 문구는 언어에 맞춰 보여 줘야 하므로 메시지 코드를 함께 들고 다닌다.
 * 코드가 없거나 번들에 없으면 여기 담긴 한국어 문구가 그대로 쓰인다.
 */
public class InquiryEditConflictException extends RuntimeException {

    private final String messageCode;

    public InquiryEditConflictException(String message) {
        this(null, message);
    }

    public InquiryEditConflictException(String messageCode, String message) {
        super(message);
        this.messageCode = messageCode;
    }

    public String getMessageCode() {
        return messageCode;
    }
}
