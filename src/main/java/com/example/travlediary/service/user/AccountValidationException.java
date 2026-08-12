package com.example.travlediary.service.user;

public class AccountValidationException extends RuntimeException {

    private final String field;

    public AccountValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
