package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 선택지 하나가 받은 표 수. 집계 결과만 담는 읽기용이라 표에 대응하는 행이 아니다.
 *
 * <p>여러 개 선택 투표에서는 한 사람이 여러 선택지에 표를 줄 수 있어
 * 이 값들의 합이 투표한 사람 수와 다를 수 있다.
 */
@Data
@NoArgsConstructor
public class TravelPlanPollOptionVoteCount {
    private Long pollId;
    private Long optionId;
    private int voteCount;
}
