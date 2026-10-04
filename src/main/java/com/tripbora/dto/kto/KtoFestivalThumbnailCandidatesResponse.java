package com.tripbora.dto.kto;

import java.util.List;

public record KtoFestivalThumbnailCandidatesResponse(
        String contentId,
        List<KtoFestivalThumbnailCandidate> items
) {
    public KtoFestivalThumbnailCandidatesResponse {
        items = List.copyOf(items);
    }
}
