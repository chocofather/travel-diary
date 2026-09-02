package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
public class InfoImage {
    private Long id; // 이미지번호
    private String imageUrl; // 이미지 url
    private String sourceType; // 이미지 유입 경로
    private String sourceName; // 제공기관명
    private String externalContentId; // 외부 콘텐츠 식별자
    private String sourceTitle; // 외부 원본 제목
    private String licenseType; // 라이선스 유형
    private String sourceImageUrl; // 외부 원본 이미지 URL
    private Timestamp licenseCheckedAt; // 라이선스 확인 시각
    private Boolean isMain; // 대표 이미지 여부
    private Boolean isThumbnail; // 목록 썸네일 여부
    private Integer orderIndex; // 정렬 순서
    private Timestamp createdAt; // 생성일
    private Long infoId; // 정보번호
}
