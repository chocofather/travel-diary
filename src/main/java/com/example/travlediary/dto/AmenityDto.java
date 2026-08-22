package com.example.travlediary.dto;

import lombok.Data;

@Data
public class AmenityDto {
    private Integer id; // amenities.id
    private String code;       // amenities.name 또는 translation name
    private String name;    // 언어별 amenity명 (예: '와이파이')
    private String iconUrl; // amenities.icon_url. NULL 이면 화면에서 code 기반 .png 로 대체한다

}
