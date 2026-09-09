package com.example.travlediary.service.translation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TranslationProviderMetadata {
    public static final String PROVIDER = "GOOGLE";

    private final String profile;

    public TranslationProviderMetadata(
            @Value("${translation.google.profile:google-v3-general-v1}") String profile) {
        if (profile == null || profile.isBlank()) {
            throw new IllegalArgumentException("번역 profile은 비어 있을 수 없습니다.");
        }
        String normalized = profile.trim();
        if (normalized.length() > 64 || !normalized.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("번역 profile 형식이 올바르지 않습니다.");
        }
        this.profile = normalized;
    }

    public String provider() {
        return PROVIDER;
    }

    public String profile() {
        return profile;
    }
}
