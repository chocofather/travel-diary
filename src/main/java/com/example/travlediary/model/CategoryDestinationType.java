package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * category_destination_types 한 행.
 * 카테고리가 어떤 여행지 유형에 적용 가능한지 정의하는 마스터 매핑이다.
 */
@Data
@NoArgsConstructor
public class CategoryDestinationType {
    private Long categoryId;          // categories.id
    private String destinationType;   // DestinationType enum 이름
}
