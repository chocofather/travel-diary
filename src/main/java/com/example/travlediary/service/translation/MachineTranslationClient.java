package com.example.travlediary.service.translation;

public interface MachineTranslationClient {
    default void prepare() {
    }

    MachineTranslation translate(String sourceText, String sourceLanguage, String targetLanguage);
}
