package com.example.travlediary.dto;

import lombok.Data;

@Data
public class RandomDestinationDto {
    private Long destinationId;
    private String destinationName;
    private String shortDescription;
    private String imageUrl;
    // 대표 이미지가 공공누리 제3유형(변경금지)이면 true → 잘리지 않게(contain) 그린다
    private boolean imageNoDerivatives;
    private Long countryId;
    private String countryName;
    private Long regionId;
    private String regionName;

    public String getDetailUrl() {
        return destinationId == null ? null : "/destinations/" + destinationId;
    }
}
