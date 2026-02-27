package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DestinationCategory {
    private Long categoryId; // 카테고리 번호
    private Long destinationId; // 여행지 번호
}
