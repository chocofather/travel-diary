package com.example.travlediary.dto;

import com.example.travlediary.model.InfoPeriod;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import lombok.Data;

import java.sql.Timestamp;
import java.util.List;

@Data
public class AdminTravelInfoDetailDto {

    private Long id;
    private String title;
    private String content;
    private TravelInfoScope scope;
    private TravelInfoContentType contentType;
    private Long categoryId;
    private String categoryName;
    private Integer views;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private List<InfoPeriod> periods = List.of();
}
