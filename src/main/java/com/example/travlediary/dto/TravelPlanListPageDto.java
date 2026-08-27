package com.example.travlediary.dto;

/**
 * 함께 계획하기 목록의 상태.
 *
 * <p>진행 중인 여행과 완료된 여행은 한 화면에 위아래로 함께 놓인다.
 * 두 목록 자체는 따로 내려가고, 여기에는 각 구역의 전체 건수와
 * 완료된 여행의 쪽 상태만 담는다.
 *
 * <p>쪽을 나누는 것은 완료된 여행뿐이다. 진행 중인 여행은 보통 몇 건뿐이라
 * 전부 보여 주므로 {@code activeCount} 는 화면에 그려진 수와 언제나 같다.
 */
public record TravelPlanListPageDto(int activeCount,
                                    int completedCount,
                                    int completedPage,
                                    int completedTotalPages,
                                    int pageSize) {
}
