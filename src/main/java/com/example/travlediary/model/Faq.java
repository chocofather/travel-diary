package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;


@Data
@NoArgsConstructor
public class Faq {
    private Long id; // 자주묻는질문 번호
    private String question; // 질문
    private String answer; // 답변
    private Long orderIndex; // faq 정렬 순서
    private Boolean isVisible; // 표시여부 숨김, 보여짐 TRUE 노출, FALSE 숨김
    private Timestamp createdAt; // 생성일
    private Timestamp updatedAt; // 수정일
    private Long categoryId; // 카테고리 id
    private Long userId; // 회원번호
}
