package com.example.travlediary.model.translation;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class UserPostLanguageBackfillRow {
    private Long id;
    private String title;
    private String content;
    private String titleSourceLanguage;
    private String contentSourceLanguage;
    private Timestamp updatedAt;
}
