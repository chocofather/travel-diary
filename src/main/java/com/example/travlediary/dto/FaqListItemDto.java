package com.example.travlediary.dto;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class FaqListItemDto {
    private Long id;
    private String question;
    private String answer;
    private Long orderIndex;
    private boolean visible;
    private Long categoryId;
    private String categoryName;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
