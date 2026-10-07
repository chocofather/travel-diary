package com.tripbora.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 여행지 중복 판별용 한 줄. 여행지 하나에 저장된 언어별 이름마다 한 줄씩 온다.
 * 이름 정규화와 비교는 {@code DestinationDuplicateService} 가 한다.
 */
@Data
@NoArgsConstructor
public class DestinationDuplicateIndexRow {
    private Long destinationId;
    private Long regionId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String sourceType;
    private String externalContentId;
    private String googlePlaceId;
    private String languageCode;
    private String name;
}
