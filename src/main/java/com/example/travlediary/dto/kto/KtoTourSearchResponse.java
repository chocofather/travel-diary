package com.example.travlediary.dto.kto;

import java.util.List;

public record KtoTourSearchResponse(
        int pageNo,
        int numOfRows,
        int totalCount,
        List<KtoTourSearchItemResponse> items
) {
}
