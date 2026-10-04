package com.tripbora.dto.wikidata;

import java.util.List;
import java.util.Map;

public record WikidataDestinationPreview(
        String qid,
        Map<String, String> names,
        Map<String, String> shortDescriptions,
        String countryQid,
        String country,
        List<String> regionPath,
        Double latitude,
        Double longitude,
        String imageFileName,
        String imageUrl,
        String imagePageUrl,
        RegionMatch regionMatch,
        TravelInfo travelInfo,
        /*
         * 중국어 표기 자동 변환으로 채운 간단 설명. 등록폼 언어(zh-CN·zh-TW) → 변환 전 원문의 Wikidata 언어 코드.
         * 여기 있는 언어의 shortDescriptions 값은 원문이 아니라 Wikipedia 변환기로 반대 표기에서 바꾼 문장이다.
         */
        Map<String, String> shortDescriptionConversions
) {
    /**
     * 여행 상세정보. 구조화된 Wikidata 값 중 비교적 안정적인 공식 웹사이트(P856)와 전화번호(P1329)만 자동입력한다.
     * 운영시간(P3025)·요금(P2555)은 요일·시각·조건이 항목과 한정자로 흩어져 있고 확인 시점이 불명확해 자동입력하지 않고,
     * 값이 있다는 사실만 알려 관리자가 공식 웹사이트에서 확인하게 한다.
     */
    public record TravelInfo(String homepageUrl, String contactNumber,
                             boolean openingHoursStated, boolean admissionFeeStated) {
    }

    public record RegionMatch(Long countryId, Long regionId, boolean matched, String message,
                              List<RegionPathItem> path) {
        public record RegionPathItem(Long id, String regionName) {
        }
    }
}
