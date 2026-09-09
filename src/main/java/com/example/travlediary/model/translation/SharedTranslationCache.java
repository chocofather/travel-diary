package com.example.travlediary.model.translation;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class SharedTranslationCache {
    private Long id;
    private byte[] sourceHash;
    private String sourceLanguage;
    private String targetLanguage;
    private String provider;
    private String translationProfile;
    private String detectedSourceLanguage;
    private String translatedText;
    private String status;
    private String leaseToken;
    private Timestamp leaseExpiresAt;
    private Timestamp retryAfter;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
