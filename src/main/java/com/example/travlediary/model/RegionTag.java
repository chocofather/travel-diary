package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RegionTag {
    private Long id; // 태그번호
    private String nameKo; // 한국명 바다, 산 등 여행지 태그
    private String nameEn; // 영어명
}
