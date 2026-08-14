package com.example.travlediary.dto;

import lombok.Data;

@Data
public class RandomDestinationDto {
    private Long destinationId;
    private String destinationName;
    private String shortDescription;
    private String imageUrl;
    private String countryName;
    private String regionName;

    public String getDetailUrl() {
        return destinationId == null ? null : "/destinations/" + destinationId;
    }
}
