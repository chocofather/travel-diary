package com.example.travlediary.service.user;

public class RegistrationValidationException extends RuntimeException {

    private final String field;
    /** messages 번들의 키. 없으면 컨트롤러가 기존 메시지를 그대로 쓴다. */
    private final String messageCode;

    public RegistrationValidationException(String field, String message) {
        this(field, message, null);
    }

    public RegistrationValidationException(String field, String message, String messageCode) {
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
