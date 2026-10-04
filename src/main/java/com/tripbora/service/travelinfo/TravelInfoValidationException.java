package com.tripbora.service.travelinfo;

public class TravelInfoValidationException extends RuntimeException {

    private final String field;

    public TravelInfoValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
