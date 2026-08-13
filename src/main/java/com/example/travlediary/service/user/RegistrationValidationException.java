package com.example.travlediary.service.user;

public class RegistrationValidationException extends RuntimeException {

    private final String field;

    public RegistrationValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
