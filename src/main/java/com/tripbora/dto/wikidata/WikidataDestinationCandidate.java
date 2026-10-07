package com.tripbora.dto.wikidata;

import com.tripbora.service.destination.DestinationDuplicateCheck;

/**
 * Wikidata 검색 후보 한 건.
 *
 * @param duplicate 이미 등록된 여행지인지 공통 중복 판별 결과. 검색 상세 조회에서만 채우고, 그 전에는 null
 */
public record WikidataDestinationCandidate(
        String qid,
        String name,
        String nameLanguageCode,
        String shortDescription,
        String descriptionLanguageCode,
        String country,
        String region,
        String imageFileName,
        String imageUrl,
        DestinationDuplicateCheck duplicate
) {
    public WikidataDestinationCandidate(String qid, String name, String nameLanguageCode, String shortDescription,
                                        String descriptionLanguageCode, String country, String region,
                                        String imageFileName, String imageUrl) {
        this(qid, name, nameLanguageCode, shortDescription, descriptionLanguageCode, country, region,
                imageFileName, imageUrl, null);
    }

    public WikidataDestinationCandidate withDuplicate(DestinationDuplicateCheck check) {
        return new WikidataDestinationCandidate(qid, name, nameLanguageCode, shortDescription,
                descriptionLanguageCode, country, region, imageFileName, imageUrl, check);
    }
}
