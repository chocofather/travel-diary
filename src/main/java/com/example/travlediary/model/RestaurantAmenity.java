package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RestaurantAmenity {
    private Long restaurantId; // 맛집 PK (restaurant_info.destination_id)
    private Integer amenityId; // 편의시설 PK (amenities.id)
}
