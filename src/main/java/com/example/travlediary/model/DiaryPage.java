package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * diary_pages 한 행. 다이어리 한 권의 페이지이며,
 * 같은 날짜(page_date)에 여러 페이지를 만들 수 있고 순서는 page_order 로 관리한다.
 */
@Data
@NoArgsConstructor
public class DiaryPage {

    private Long id; // 페이지 번호 (PK)
    private Long diaryId; // 다이어리 번호
    private LocalDate pageDate; // 페이지 날짜
    private Integer pageOrder; // 다이어리 안에서의 순서 (1부터)
    private String backgroundType; // 배경 유형
    private String paperColor; // 종이 바탕색 #RRGGBB (없으면 null = 기본 종이색)
    private String pageHeader; // 날짜 옆에 적는 짧은 한 줄 메모 (없으면 null)
    private String pageHeaderFont; // 그 한 줄 메모의 글꼴 (기본 'DEFAULT')
    private Boolean pageHeaderBold; // 그 한 줄 메모를 굵게 쓸지 (기본 false)
    private String content; // 페이지 본문 (없으면 null)
    private Timestamp createdAt; // 생성일
    private Timestamp updatedAt; // 수정일
}
