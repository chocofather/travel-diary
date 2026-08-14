package com.example.travlediary.dto;

import lombok.Data;

@Data
public class RandomTravelRegionDto {
    private Long countryId;
    private String countryName;
    private Long regionId;
    private String regionName;
}
