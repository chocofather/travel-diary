package com.tripbora.dto.kto;

public record KtoFestivalThumbnailCandidate(
        String selectionKey,
        String imageUrl,
        String imageName,
        String imageRole,
        String licenseType,
        boolean selectable,
        String unavailableReason
) {
}
