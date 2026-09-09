package com.example.travlediary.service.translation;

public class TranslationDailyLimitException extends RuntimeException {
    public TranslationDailyLimitException() {
        super("Daily translation character limit reached");
    }
}
