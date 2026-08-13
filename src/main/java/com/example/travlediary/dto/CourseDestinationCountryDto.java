package com.example.travlediary.dto;

import lombok.Data;

@Data
public class CourseDestinationCountryDto {
    private Long destinationId;
    private Long countryId;
    private String countryName;
}
