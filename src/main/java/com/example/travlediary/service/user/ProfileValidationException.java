package com.example.travlediary.service.user;

/**
 * 프로필 입력값 오류.
 *
 * <p>마이페이지는 공개 화면이라 언어에 맞는 문구를 보여 줘야 하므로 메시지 코드를 함께 들고 다닌다.
 * 코드가 없거나 번들에 없으면 여기 담긴 한국어 문구가 그대로 쓰인다.
 */
public class ProfileValidationException extends RuntimeException {

    private final String field;
    private final String messageCode;

    public ProfileValidationException(String field, String message) {
        this(field, null, message);
    }

    public ProfileValidationException(String field, String messageCode, String message) {
        super(message);
        this.field = field;
        this.messageCode = messageCode;
    }

    public String getField() {
        return field;
    }

    public String getMessageCode() {
        return messageCode;
    }
}
