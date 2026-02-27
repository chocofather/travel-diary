package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Category {
    private Long id; // 카테고리 번호
    private String name; // 카테고리 이름
}
