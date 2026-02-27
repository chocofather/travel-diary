package com.example.travlediary.dto;

import lombok.Data;

@Data
public class RandomDestinationDto {
    private Long destinationId;
    private String destinationName;
    private String shortDescription;
    private String imageUrl;
}
