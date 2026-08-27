package com.example.travlediary.dto;

import lombok.Data;

/**
 * 반응 요약 한 줄. (메시지, 반응 종류) 하나에 대한 집계다.
 *
 * <p>한 문장으로 여러 메시지의 요약을 함께 읽어 온다.
 * 메시지마다 따로 묻지 않으므로 기록 40건을 읽어도 조회는 한 번 더 나갈 뿐이다.
 */
@Data
public class TravelPlanChatReactionRow {
    private Long messageId;
    /** DB 에 저장된 이름. 화면으로 나가기 전에 enum 으로 한 번 걸러진다. */
    private String reactionType;
    private int count;
    /** 지금 보는 사람이 이 반응을 눌렀는지. 서버가 센 값이다. */
    private boolean reacted;
}
