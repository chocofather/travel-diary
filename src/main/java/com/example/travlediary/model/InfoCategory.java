package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class InfoCategory {
    private Long id; // 정보카테고리번호
    private String name; // 정보카테고리이름
    private TravelInfoContentType contentType; // 여행정보 유형
    private Integer displayOrder; // 표시 순서
    private Boolean isVisible; // 노출 여부
}
