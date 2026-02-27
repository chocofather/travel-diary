package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
public class Inquiry {
    private Long id; // 1:1문의 번호
    private String subject; // 질문명
    private String content; // 문의 내용
    private InquiryStatus status; // PENDING(처리전):기본값, IN_PROGRESS(처리중), ANSWERED(답변완료), CANCELLED(사용자 취소)
    private Timestamp createdAt;
    private Long userId;

}
