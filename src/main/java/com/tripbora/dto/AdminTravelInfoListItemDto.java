package com.tripbora.dto;

import com.tripbora.model.TravelInfoContentType;
import com.tripbora.model.TravelInfoScope;
import lombok.Data;

import java.sql.Timestamp;
import java.time.LocalDate;

@Data
public class AdminTravelInfoListItemDto {

    private Long id;
    private String title;
    private TravelInfoScope scope;
    private TravelInfoContentType contentType;
    private Long categoryId;
    private String categoryName;
    private Integer views;
    private Boolean homeFeatured;
    private Integer homeFeaturedOrder;
    private Timestamp createdAt;
    private LocalDate startDate;
    private LocalDate endDate;
}
