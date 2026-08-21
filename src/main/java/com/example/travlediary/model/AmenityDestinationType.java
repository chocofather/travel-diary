package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * amenity_destination_types 한 행.
 * 편의시설이 어떤 여행지 유형에 적용 가능한지 정의하는 마스터 매핑이다.
 */
@Data
@NoArgsConstructor
public class AmenityDestinationType {
    private Integer amenityId;        // amenities.id
    private String destinationType;   // DestinationType enum 이름
}
