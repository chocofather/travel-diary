    package com.example.travlediary.model;

    import lombok.Data;
    import lombok.NoArgsConstructor;

    import java.sql.Timestamp;


    @Data
    @NoArgsConstructor
    public class DestinationImage {
        private Long id; // 여행지 이미지 번호
        private String imageUrl; // 이미지 Url
        private Timestamp createdAt; // 생성일
        private Boolean isMain; // 메인이미지 여부
        private Boolean isSlide; // 슬라이드 여부
        private Integer orderIndex; // 이미지 순서
        private Long destinationId; // 여행지번호
    }
