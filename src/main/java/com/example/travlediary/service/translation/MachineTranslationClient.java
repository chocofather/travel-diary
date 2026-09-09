package com.example.travlediary.service.translation;

public interface MachineTranslationClient {
    default void prepare() {
    }

    MachineTranslation translate(String sourceText, String sourceLanguage, String targetLanguage);

    default MachineTranslation translate(
            String sourceText, String sourceLanguage, String targetLanguage, String mimeType) {
        return translate(sourceText, sourceLanguage, targetLanguage);
    }
}
