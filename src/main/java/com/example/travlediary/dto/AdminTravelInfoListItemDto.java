package com.example.travlediary.dto;

import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import lombok.Data;

import java.sql.Timestamp;

@Data
public class AdminTravelInfoListItemDto {

    private Long id;
    private String title;
    private TravelInfoScope scope;
    private TravelInfoContentType contentType;
    private Long categoryId;
    private String categoryName;
    private Integer views;
    private Timestamp createdAt;
}
