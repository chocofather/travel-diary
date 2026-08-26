package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 진행 상태별 투표 수. 집계 결과만 담는 읽기용이라 표에 대응하는 행이 아니다.
 *
 * <p>탭의 숫자만 필요할 때 쓴다. 숫자를 알려고 목록 전체를 읽지 않기 위한 것이다.
 */
@Data
@NoArgsConstructor
public class TravelPlanPollStatusCount {
    private TravelPlanPollStatus status;
    private int pollCount;
}
