package com.example.travlediary.dto.kto;

import java.util.List;

public record KtoPhotoSearchResponse(
        int pageNo,
        int numOfRows,
        int totalCount,
        List<KtoPhotoSearchItemResponse> items
) {
}
