package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ShopAmenity {
    private Long shopId;      // shop_info.destination_id
    private Integer amenityId; // amenities.id
}
