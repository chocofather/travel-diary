package com.example.travlediary.dto.kto;

import java.util.List;

/**
 * 한 페이지 분량의 후보 목록.
 *
 * <p>건수는 모두 TourAPI 원본 총건수가 아니라, 선택한 지역·콘텐츠 유형에 실제로 해당하는
 * 후보를 기준으로 센 값이다 (숙박·음식점·축제는 애초에 후보에 없다).
 *
 * @param totalCount      등록상태 필터까지 적용한 결과 수. 페이징 기준이다.
 * @param allCount        같은 지역·유형의 전체 후보 수
 * @param newCount        그중 미등록
 * @param registeredCount 그중 등록완료
 */
public record KtoTourAreaCandidateListResponse(
        int pageNo,
        int numOfRows,
        int totalCount,
        int allCount,
        int newCount,
        int registeredCount,
        List<KtoTourAreaCandidateResponse> items
) {
    public KtoTourAreaCandidateListResponse {
        items = List.copyOf(items);
    }
}
