package com.tripbora.dto.kto;

import java.util.List;

public record KtoFestivalSearchResponse(
        int pageNo,
        int numOfRows,
        int totalCount,
        List<KtoFestivalSearchItemResponse> items
) {
}
