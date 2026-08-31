package com.example.travlediary.service.travelinfo;

public class FestivalValidationException extends RuntimeException {

    private final String field;

    public FestivalValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
