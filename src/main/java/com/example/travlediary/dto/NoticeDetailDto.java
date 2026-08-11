package com.example.travlediary.dto;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class NoticeDetailDto {
    private Long id;
    private String title;
    private String content;
    private int views;
    private Timestamp createdAt;
}
