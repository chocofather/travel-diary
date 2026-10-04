package com.tripbora.service.notice;

public class NoticeValidationException extends RuntimeException {
    private final String field;

    public NoticeValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
