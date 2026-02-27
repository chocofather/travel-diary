package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Amenity {
    private Integer id;      // 편의시설 고유번호 (PK)
    private String code;     // 편의시설명 (예: "와이파이", "수영장", "주차장")
}
