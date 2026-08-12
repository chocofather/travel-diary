package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
public class InquiryAnswer {
    private Long id; // 답변번호
    private String content; // 내용
    private Timestamp createdAt; // 생성일
    private Timestamp updatedAt; // 수정일
    private Long inquiryId; // 1:1문의 번호
    private Long userId; // 회원번호

}
