    package com.example.travlediary.model;

    import lombok.Data;
    import lombok.NoArgsConstructor;

    import java.sql.Timestamp;


    @Data
    @NoArgsConstructor
    public class DestinationImage {
        private Long id; // 여행지 이미지 번호
        private String imageUrl; // 이미지 Url
        private String sourceType; // 이미지 유입 경로
        private String sourceName; // 사진 제공기관명
        private String externalContentId; // 외부 API 콘텐츠 식별자
        private String sourceTitle; // 외부 원본 사진 제목
        private String photographer; // 촬영자/저작자
        private String licenseType; // 라이선스 유형
        private String sourceImageUrl; // 외부 제공처의 원본 이미지 URL
        private Timestamp licenseCheckedAt; // 라이선스 조건 확인 시각
        private Timestamp createdAt; // 생성일
        private Boolean isMain; // 메인이미지 여부
        private Boolean isSlide; // 슬라이드 여부
        private Integer orderIndex; // 이미지 순서
        private Long destinationId; // 여행지번호
    }
