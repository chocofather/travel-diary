package com.example.travlediary.service.translation;

public record DetectedLanguage(String code, double confidence) {
    public static DetectedLanguage undetermined() {
        return new DetectedLanguage("und", 0.0d);
    }
}
