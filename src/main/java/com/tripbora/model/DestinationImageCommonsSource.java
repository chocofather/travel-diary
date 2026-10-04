package com.tripbora.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Wikimedia Commons 사진의 destination_image_sources 행.
 * 저장 직전 Commons API에서 다시 조회한 값만 담고, 긴 원문도 자르지 않는다.
 * 기존 destination_images 의 짧은 utf8mb3 출처 컬럼에는 이중 저장하지 않는다.
 */
@Data
public class DestinationImageCommonsSource {
    private Long destinationImageId;
    private String sourceName;
    /** Commons MediaInfo ID (M + 파일 페이지 ID). */
    private String externalContentId;
    private String sourceTitle;
    private String authorText;
    private String workPageUrl;
    private String originalImageUrl;
    private String licenseType;
    private String licenseName;
    private String licenseVersion;
    private String licenseUrl;
    private String attributionText;
    private String sourceCredit;
    private String customAttribution;
    private String creditLinksJson;
    private String licenseEvidenceUrl;
    private String licenseEvidenceDetail;
    private Long licenseEvidenceRevisionId;
    private String licenseConditions;
    private String licenseRestrictions;
    private Boolean attributionRequired;
    private Boolean changesRequired;
    private Boolean shareAlikeRequired;
    private Boolean contentModified;
    private LocalDateTime licenseCheckedAt;
    private String wikidataQid;
    private String commonsFileTitle;
}
