package com.example.travlediary.dto;

import lombok.Data;

import java.util.List;

@Data
public class RandomTravelResultDto {
    private String scope;
    private Long countryId;
    private String countryName;
    private Long regionId;
    private String regionName;
    private List<RandomDestinationDto> recommendedDestinations;
}
