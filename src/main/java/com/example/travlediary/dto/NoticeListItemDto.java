package com.example.travlediary.dto;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class NoticeListItemDto {
    private Long id;
    private String title;
    private boolean pinned;
    private int views;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
