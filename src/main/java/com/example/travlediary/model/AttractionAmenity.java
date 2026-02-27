package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AttractionAmenity {
    private Long attractionId; // 관광지 PK (attraction_info.destination_id)
    private Integer amenityId; // 편의시설 PK (amenities.id)
}
