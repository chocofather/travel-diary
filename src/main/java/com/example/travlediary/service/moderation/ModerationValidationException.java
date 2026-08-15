package com.example.travlediary.service.moderation;

import lombok.Getter;

@Getter
public class ModerationValidationException extends RuntimeException {

    private final String field;

    public ModerationValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
}
