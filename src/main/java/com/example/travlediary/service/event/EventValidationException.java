package com.example.travlediary.service.event;

import lombok.Getter;

@Getter
public class EventValidationException extends RuntimeException {

    private final String field;

    public EventValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
}
