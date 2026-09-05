package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * 여행정보의 언어별 제목·본문.
 * GENERAL / FESTIVAL 이 같은 테이블을 함께 쓰며, content 는 base 와 같은 Quill HTML 을 담는다.
 */
@Data
@NoArgsConstructor
public class TravelInfoTranslation {
    private Long id; // 여행정보 다국어 번호
    private Long travelInfoId; // 여행정보 번호
    private String languageCode; // 언어 코드 예: 'ko', 'en'
    private String title; // 여행정보 다국어 제목
    private String content; // 여행정보 다국어 본문 (Quill HTML)
}
