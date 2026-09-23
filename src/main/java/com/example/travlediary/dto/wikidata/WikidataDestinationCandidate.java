package com.example.travlediary.dto.wikidata;

public record WikidataDestinationCandidate(
        String qid,
        String name,
        String nameLanguageCode,
        String shortDescription,
        String descriptionLanguageCode,
        String country,
        String region,
        String imageFileName,
        String imageUrl
) {
}
