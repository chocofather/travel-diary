package com.tripbora.model;

import lombok.Data;

import java.time.LocalDateTime;

/** Wikipedia 원문 출처. destination_translations 한 행에 선택적으로 연결된다. */
@Data
public class DestinationTranslationSource {
    private Long destinationTranslationId;
    private String wikidataQid;
    private String sourceTitle;
    private String sourceUrl;
    private Long sourceRevisionId;
    private String sourceLanguage;
    private String sourceVariant;
    private String licenseName;
    private String licenseUrl;
    private String attributionText;
    private byte[] originalContentSha256;
    private boolean contentModified;
    private LocalDateTime sourceCheckedAt;
}
