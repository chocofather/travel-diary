package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AccommodationAmenity {
    private Long accommodationId; // 숙소(숙박) PK (accommodation_info.destination_id)
    private Integer amenityId;    // 편의시설 PK (amenities.id)
}
