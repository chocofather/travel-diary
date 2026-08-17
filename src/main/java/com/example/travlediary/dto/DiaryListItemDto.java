package com.example.travlediary.dto;

import lombok.Data;

import java.time.LocalDate;

/** 일기장형 목록 화면 한 칸. 표지 정보와 페이지 수만 담는다. */
@Data
public class DiaryListItemDto {

    private Long id;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private String coverImageUrl;
    private String coverStyle;
    private int pageCount; // 다이어리에 속한 페이지 수 (없으면 0)
}
