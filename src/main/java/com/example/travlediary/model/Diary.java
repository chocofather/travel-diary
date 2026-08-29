package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * diaries 한 행. 여행 한 번이 다이어리 한 권이다.
 */
@Data
@NoArgsConstructor
public class Diary {

    private Long id; // 다이어리 번호 (PK)
    private Long userId; // 회원 번호
    private String title; // 제목
    private LocalDate startDate; // 여행 시작일
    private LocalDate endDate; // 여행 종료일
    private String coverImageUrl; // 표지 이미지 경로
    private String coverStyle; // 표지 스타일
    private String notebookType; // 다이어리 내부(속지) 타입 - CLASSIC / SPIRAL
    private Timestamp createdAt; // 생성일
    private Timestamp updatedAt; // 수정일
}
