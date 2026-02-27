package com.example.travlediary.dto;

import lombok.Data;

@Data
public class AmenityDto {
    private Integer id; // amenities.id
    private String code;       // amenities.name 또는 translation name
    private String name;    // 언어별 amenity명 (예: '와이파이')

}
