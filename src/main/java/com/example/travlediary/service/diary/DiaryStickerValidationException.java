package com.example.travlediary.service.diary;

public class DiaryStickerValidationException extends RuntimeException {
    private final String field;

    public DiaryStickerValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
