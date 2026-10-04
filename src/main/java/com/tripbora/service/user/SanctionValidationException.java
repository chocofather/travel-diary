package com.tripbora.service.user;

import lombok.Getter;

@Getter
public class SanctionValidationException extends RuntimeException {

    private final String field;

    public SanctionValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
}
