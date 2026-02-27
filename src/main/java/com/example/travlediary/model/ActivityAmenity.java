package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ActivityAmenity {
    private Long activityId;   // activity_info.destination_id (PK, FK)
    private Integer amenityId; // amenities.id (PK, FK)
}
