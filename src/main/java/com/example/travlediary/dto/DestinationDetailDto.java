package com.example.travlediary.dto;

import com.example.travlediary.model.*;
import lombok.Data;

import java.util.List;

@Data
public class DestinationDetailDto {
    private Destination destination;
    private AttractionInfo attractionInfo;
    private List<AmenityDto> attractionAmenities;

    private AccommodationInfo accommodationInfo;
    private List<AmenityDto> accommodationAmenities;

    private RestaurantInfo restaurantInfo;
    private List<AmenityDto> restaurantAmenities;

    private ActivityInfo activityInfo;
    private List<AmenityDto> activityAmenities;

    private ShopInfo shopInfo;
    private List<AmenityDto> shopAmenities;

    private List<DestinationImage> images;

    private List<DestinationTranslation> translations;

    private List<Long> categoryIds;

    /** 대표 카테고리(등록 카테고리 중 하나). 대표가 없는 기존 데이터는 null. */
    private Long mainCategoryId;

}
