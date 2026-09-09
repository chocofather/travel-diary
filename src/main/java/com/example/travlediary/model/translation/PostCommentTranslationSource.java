package com.example.travlediary.model.translation;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class PostCommentTranslationSource {
    private Long contentId;
    private String sourceText;
    private String sourceLanguage;
    private Timestamp updatedAt;
}
