package com.example.travlediary.repository.travelplan;

import com.example.travlediary.dto.TravelPlanChatReactionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 메시지 반응 전용 매퍼. travel_plan_chat_message_reactions 한 표만 다룬다.
 *
 * <p>한 사람이 한 메시지에 남길 수 있는 반응은 하나뿐이고,
 * 그것은 (message_id, member_id) UNIQUE 가 막는다.
 * 여기서는 그 제약을 그대로 쓰고 애플리케이션에서 다시 세지 않는다.
 */
@Mapper
public interface TravelPlanChatReactionMapper {

    /**
     * 반응을 남기거나, 이미 다른 것을 눌러 두었으면 그것을 이 종류로 바꾼다.
     *
     * <p>지우고 넣는 두 문장으로 나누지 않는다. 그 사이에 행이 없는 순간이 생기고
     * 그때 다른 요청이 끼어들면 한 사람의 행이 둘이 될 수 있다.
     *
     * @return 새로 남겼으면 1, 종류가 바뀌었으면 2 (MySQL 의 관례다)
     */
    int upsertReaction(@Param("messageId") Long messageId,
                       @Param("memberId") Long memberId,
                       @Param("reactionType") String reactionType);

    /**
     * 눌러 둔 그 반응을 거둔다. 종류까지 같을 때만 지워진다.
     *
     * @return 1 이면 같은 것을 다시 눌러 방금 거뒀고,
     *         0 이면 아무 것도 없었거나 다른 종류를 눌러 둔 상태다.
     *         이 값으로 "취소" 와 "새로 누르기/바꾸기" 를 가른다.
     */
    int deleteReaction(@Param("messageId") Long messageId,
                       @Param("memberId") Long memberId,
                       @Param("reactionType") String reactionType);

    /**
     * 여러 메시지의 반응 요약을 한 번에 읽는다.
     *
     * <p>개수와 "내가 눌렀는지" 를 한 문장에서 함께 구한다. 둘을 따로 물으면
     * 조회가 두 번 나가고, 메시지마다 물으면 기록 한 쪽(40건)에 40번이 나간다.
     * (message_id, member_id) UNIQUE 의 앞 컬럼을 그대로 탄다.
     *
     * <p>한 사람은 하나만 남기므로 한 메시지 안에서 reacted 가 1 인 줄은 많아야 하나다.
     *
     * @param messageIds 비어 있으면 부르지 않는다(IN () 은 SQL 오류다)
     * @param memberId   지금 보는 사람. 이 사람이 누른 것만 reacted 로 표시된다
     */
    List<TravelPlanChatReactionRow> findSummaries(@Param("messageIds") List<Long> messageIds,
                                                  @Param("memberId") Long memberId);
}
