package com.example.travlediary.service.translation;

public class TranslationMonthlyLimitException extends RuntimeException {
    public TranslationMonthlyLimitException() {
        super("현재 새 번역 요청을 사용할 수 없습니다.");
    }
}
