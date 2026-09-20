    package com.example.travlediary.model;

    import lombok.Data;
    import lombok.NoArgsConstructor;

    import java.net.URI;
    import java.sql.Timestamp;
    import java.util.Locale;


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
        private String licenseDetail; // 라이선스 세부 이용조건
        private String sourceUrl; // 원본/출처 페이지 URL
        private String sourceImageUrl; // 외부 제공처의 원본 이미지 URL
        private Timestamp licenseCheckedAt; // 라이선스 조건 확인 시각
        private Timestamp createdAt; // 생성일
        private Boolean isMain; // 메인이미지 여부
        private Boolean isSlide; // 슬라이드 여부
        private Integer orderIndex; // 이미지 순서
        private Long destinationId; // 여행지번호

        public String getLicenseLabel() {
            return DestinationImageLicenseType.displayName(licenseType);
        }

        /** 세부조건이 있으면 우선하고, 없으면 사람이 읽는 기본 라벨을 쓴다. */
        public String getLicenseDisplay() {
            String detail = text(licenseDetail);
            if (detail != null) {
                return detail;
            }
            return getLicenseLabel();
        }

        public boolean isAttributionPresent() {
            return text(sourceName) != null || getLicenseDisplay() != null
                    || text(photographer) != null || getSafeSourceUrl() != null;
        }

        /** 외부 링크로 렌더링해도 되는 절대 HTTP(S) URL만 돌려준다. */
        public String getSafeSourceUrl() {
            String candidate = text(sourceUrl);
            if (candidate == null) {
                return null;
            }
            try {
                URI uri = URI.create(candidate);
                String scheme = uri.getScheme() == null
                        ? ""
                        : uri.getScheme().toLowerCase(Locale.ROOT);
                if (!("http".equals(scheme) || "https".equals(scheme))
                        || uri.getHost() == null
                        || uri.getUserInfo() != null) {
                    return null;
                }
                return uri.toString();
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        private String text(String value) {
            return value == null || value.isBlank() ? null : value.strip();
        }
    }
