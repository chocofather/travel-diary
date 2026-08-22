package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Amenity {
    private Integer id;      // 편의시설 고유번호 (PK)
    private String code;     // 편의시설명 (예: "와이파이", "수영장", "주차장")
    /** amenities.icon_url. 기존 데이터는 NULL 이며 화면에서 code 기반 .png 로 대체한다. */
    private String iconUrl;
}
