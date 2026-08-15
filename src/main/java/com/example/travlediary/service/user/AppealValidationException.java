package com.example.travlediary.service.user;

import lombok.Getter;

@Getter
public class AppealValidationException extends RuntimeException {

    private final String field;

    public AppealValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
}
