package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FaqCategory {
    private Long id; // 자주묻는질문 카테고리 번호
    private String categoryName; // 자주묻질문 카테고리 이름 예: 결제, 회원, 서비스 이용방법
}
