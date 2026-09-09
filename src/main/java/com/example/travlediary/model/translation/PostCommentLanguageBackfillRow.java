package com.example.travlediary.model.translation;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class PostCommentLanguageBackfillRow {
    private Long id;
    private String content;
    private Timestamp updatedAt;
}
