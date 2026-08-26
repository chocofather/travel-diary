package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * travel_plan_poll_votes 한 행.
 *
 * <p>(poll_id, member_id) 가 UNIQUE 라 한 사람은 한 투표에 한 줄만 갖는다.
 * 선택을 바꿔도 이 줄을 새로 만들지 않고 딸린 선택(selections)만 갈아 끼운다.
 */
@Data
@NoArgsConstructor
public class TravelPlanPollVote {
    private Long id;
    private Long pollId;
    /** 투표한 사람의 방 안 id. 계정 id 가 아니다. */
    private Long memberId;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
