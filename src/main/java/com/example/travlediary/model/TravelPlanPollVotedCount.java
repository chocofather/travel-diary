package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 투표 하나에 실제로 참여한 사람 수. 목록을 그릴 때 투표 수만큼 조회가 나가지 않게 한 번에 읽는다.
 * 지금 방에 남아 있는(ACTIVE) 사람만 센다.
 */
@Data
@NoArgsConstructor
public class TravelPlanPollVotedCount {
    private Long pollId;
    private int votedMemberCount;
}
