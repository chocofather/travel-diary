package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.time.LocalDate;

@Data
@NoArgsConstructor
public class Event {

    private Long id; // 이벤트 id (PK)
    private String title; // 제목
    private String description; // 내용
    private String eventImg;    // 대표 이미지 (메인/슬라이드)
    private String posterImg;   // 포스터 이미지 (상세 포스터 용)
    private Boolean slide; // 슬라이드 여부
    private Long userId; // 회원 번호
    private LocalDate startDate; // 이벤트 시작일
    private LocalDate endDate; // 종료일
    private Timestamp createdAt; // 생성일

}
