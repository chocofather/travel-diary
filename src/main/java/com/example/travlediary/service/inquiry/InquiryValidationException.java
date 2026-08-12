package com.example.travlediary.service.inquiry;

public class InquiryValidationException extends RuntimeException {
    private final String field;

    public InquiryValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
