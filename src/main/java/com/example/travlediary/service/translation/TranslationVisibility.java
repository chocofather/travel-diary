package com.example.travlediary.service.translation;

import com.example.travlediary.config.i18n.SupportedLanguage;

public final class TranslationVisibility {
    private TranslationVisibility() {
    }

    public static boolean shouldOffer(String sourceLanguage, String targetLanguage) {
        var source = SupportedLanguage.fromLanguageTag(sourceLanguage);
        var target = SupportedLanguage.fromLanguageTag(targetLanguage);
        return source.isPresent() && target.isPresent() && source.get() != target.get();
    }
}
