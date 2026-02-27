package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RegionTagMap {
    private Long id;
    private Long categoryId; // 카테고리번호
    private Long tagId; // 태그번호
}
