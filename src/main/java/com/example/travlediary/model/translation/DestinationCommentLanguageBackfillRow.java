package com.example.travlediary.model.translation;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class DestinationCommentLanguageBackfillRow {
    private Long id;
    private String content;
    private Timestamp updatedAt;
}
