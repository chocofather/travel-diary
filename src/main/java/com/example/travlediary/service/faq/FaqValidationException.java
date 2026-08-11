package com.example.travlediary.service.faq;

public class FaqValidationException extends RuntimeException {
    private final String field;

    public FaqValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
