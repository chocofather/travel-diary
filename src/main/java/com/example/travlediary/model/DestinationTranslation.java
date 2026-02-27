package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DestinationTranslation {
    private Long id; // 여행지 다국어 번호
    private String languageCode; // 언어 코드 예: 'kr', 'en'
    private String name; // 여행지 다국어 이름
    private String description; // 여행지 다국어 설명
    private Long destinationId; // 여행지번호
    private String shortDescription; // 간단설명

}
