package com.example.travlediary.service.user;

public class SocialSignupValidationException extends RuntimeException {

    private final String field;

    public SocialSignupValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
