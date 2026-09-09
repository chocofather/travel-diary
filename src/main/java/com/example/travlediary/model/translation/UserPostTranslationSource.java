package com.example.travlediary.model.translation;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class UserPostTranslationSource {
    private Long contentId;
    private String title;
    private String titleSourceLanguage;
    private String content;
    private String contentSourceLanguage;
    private Timestamp updatedAt;
}
