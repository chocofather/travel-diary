package com.example.travlediary.model.translation;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class ContentTranslationCache {
    private Long id;
    private String contentType;
    private Long contentId;
    private String sourceField;
    private String targetLanguage;
    private byte[] sourceHash;
    private String detectedSourceLanguage;
    private String translatedText;
    private String provider;
    private String status;
    private String leaseToken;
    private Timestamp leaseExpiresAt;
    private Timestamp retryAfter;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
