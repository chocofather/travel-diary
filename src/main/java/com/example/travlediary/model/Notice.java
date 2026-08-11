package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
public class Notice {
    private Long id;
    private String title;
    private String content;
    private boolean pinned;
    private int views;
    private Long userId;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
