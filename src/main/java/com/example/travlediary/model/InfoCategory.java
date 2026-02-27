package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class InfoCategory {
    private Long id; // 정보카테고리번호
    private String name; // 정보카테고리이름
    private InfocategoryType type; // 정보카테고리 타입 '국내', '해외'


}
