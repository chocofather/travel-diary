package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * travel_plan_polls 한 행.
 *
 * <p>질문은 title 컬럼에 담긴다(varchar 200).
 * 결과 공개 시점·마감 방식·마감 시각은 컬럼만 채워 두고,
 * 실제 투표/마감은 다음 단계에서 다룬다.
 */
@Data
@NoArgsConstructor
public class TravelPlanPoll {
    private Long id;
    private Long travelPlanId;
    /** 만든 사람의 방 안 id. 계정 id 가 아니다. */
    private Long createdByMemberId;
    /** 투표 질문. */
    private String title;
    private TravelPlanPollSelectionType selectionType;
    private TravelPlanPollResultVisibility resultVisibility;
    private TravelPlanPollCloseType closeType;
    private Timestamp deadlineAt;
    private TravelPlanPollStatus status;
    private String closeReason;
    private Timestamp closedAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    /**
     * travel_plan_members 에서 함께 읽어 오는 방 안 표시 이름.
     * 이 테이블의 컬럼이 아니라 조회 결과에만 실린다.
     */
    private String createdByDisplayName;
}
