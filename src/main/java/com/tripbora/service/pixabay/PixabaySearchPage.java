package com.tripbora.service.pixabay;

import java.time.Instant;
import java.util.List;

/** Pixabay API 한 페이지(page·per_page) 응답. fetchedAt 은 외부에서 받은 시각이다. */
public record PixabaySearchPage(int totalHits, List<PixabayHit> hits, Instant fetchedAt) {

    /** 검색으로 받은 사진 한 장과 받은 시각. 저장 요청의 사진 ID를 이 값으로 확인한다. */
    record IndexedHit(PixabayHit hit, Instant fetchedAt) {
    }
}
