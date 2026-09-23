package com.example.travlediary.dto.wikidata;

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
        RegionMatch regionMatch
) {
    public record RegionMatch(Long countryId, Long regionId, boolean matched, String message) {
    }
}
