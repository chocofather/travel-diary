package com.example.travlediary.repository.travelplan;

import com.example.travlediary.model.TravelPlanPoll;
import com.example.travlediary.model.TravelPlanPollCloseReason;
import com.example.travlediary.model.TravelPlanPollOption;
import com.example.travlediary.model.TravelPlanPollOptionVoteCount;
import com.example.travlediary.model.TravelPlanPollStatusCount;
import com.example.travlediary.model.TravelPlanPollVote;
import com.example.travlediary.model.TravelPlanPollVotedCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 방 투표 전용 매퍼.
 * travel_plan_polls 와 travel_plan_poll_options 두 표만 다룬다.
 *
 * <p>이번 단계는 만들기까지다. 표 집계 관련 표(votes / vote_selections)는 읽지 않는다.
 */
@Mapper
public interface TravelPlanPollMapper {

    /** 투표 1건 등록. status 등 DB DEFAULT 를 쓰지 않고 값을 명시한다. */
    int insertPoll(TravelPlanPoll poll);

    /** 선택지 1건 등록. display_order 는 1 부터 올라간다. */
    int insertOption(TravelPlanPollOption option);

    /** 진행 중인 투표 목록. 만든 순서대로 온다. */
    List<TravelPlanPoll> findOpenPolls(@Param("travelPlanId") Long travelPlanId);

    /**
     * 끝난 투표 목록. 최근에 끝난 것부터 온다.
     * 마감 기능은 다음 단계라 지금은 대개 비어 있다.
     */
    List<TravelPlanPoll> findClosedPolls(@Param("travelPlanId") Long travelPlanId);

    /**
     * 가장 최근에 만들어진 투표부터 limit 건.
     * 채팅 타임라인에 "새 투표를 만들었어요" 를 끼워 넣을 때 쓴다.
     * 만들어졌다는 사실이 기준이라 지금 진행 중인지 끝났는지는 보지 않는다.
     */
    List<TravelPlanPoll> findRecentPolls(@Param("travelPlanId") Long travelPlanId,
                                         @Param("limit") int limit);

    /** 어떤 투표보다 앞서 만들어진 것들 중 최근 limit 건. 타임라인의 앞 페이지가 쓴다. */
    List<TravelPlanPoll> findPollsBefore(@Param("travelPlanId") Long travelPlanId,
                                         @Param("beforePollId") Long beforePollId,
                                         @Param("limit") int limit);

    /**
     * 진행 상태별 투표 수.
     * 탭에 붙는 숫자만 필요할 때 쓴다. 숫자를 알려고 목록 전체를 읽지 않는다.
     * 한 번도 나오지 않은 상태는 결과에 없으므로 호출한 쪽이 0 으로 채운다.
     */
    List<TravelPlanPollStatusCount> countPollsByStatus(@Param("travelPlanId") Long travelPlanId);

    /** 투표 1건. 방 소속 조건을 함께 걸어 다른 방의 투표를 집을 수 없게 한다. */
    TravelPlanPoll findByIdAndPlanId(@Param("id") Long id,
                                     @Param("travelPlanId") Long travelPlanId);

    /**
     * 여러 투표의 선택지를 한 번에 읽는다.
     * 투표 수만큼 조회가 나가지 않도록 호출한 쪽이 poll_id 로 묶어서 쓴다.
     */
    List<TravelPlanPollOption> findOptionsByPollIds(@Param("pollIds") List<Long> pollIds);

    /** 이 투표의 선택지 개수. 한 번에 몇 개까지 고를 수 있는지 볼 때 쓴다. */
    int countOptionsByPollId(@Param("pollId") Long pollId);

    /**
     * 넘어온 선택지 중 정말 이 투표의 것인 개수.
     * 보낸 개수와 다르면 다른 투표의 선택지가 섞인 것이다.
     */
    int countOwnedOptions(@Param("pollId") Long pollId,
                          @Param("optionIds") List<Long> optionIds);

    // ── 투표하기 ────────────────────────────────────────────

    /** 이 사람이 이 투표에 이미 넣어 둔 줄. 없으면 null. */
    TravelPlanPollVote findVoteByPollAndMember(@Param("pollId") Long pollId,
                                               @Param("memberId") Long memberId);

    /** 투표 줄 1건. (poll_id, member_id) UNIQUE 라 사람마다 하나뿐이다. */
    int insertVote(TravelPlanPollVote vote);

    /** 선택을 바꿀 때 이전 선택을 걷어 낸다. 투표 줄 자체는 그대로 둔다. */
    int deleteSelectionsByVoteId(@Param("voteId") Long voteId);

    /** 고른 선택지 1건. (vote_id, option_id) UNIQUE 가 같은 것을 두 번 넣지 못하게 막는다. */
    int insertSelection(@Param("voteId") Long voteId, @Param("optionId") Long optionId);

    /** 내가 지금 고르고 있는 선택지들. 상세 화면에서 미리 체크해 두는 데 쓴다. */
    List<Long> findSelectedOptionIds(@Param("voteId") Long voteId);

    // ── 집계 ────────────────────────────────────────────────

    /**
     * 이 투표에 참여한 사람 수.
     * 지금 방에 남아 있는(ACTIVE) 사람만 센다. 나갔거나 내보내진 사람은 빠진다.
     */
    int countVotedMembers(@Param("pollId") Long pollId);

    /** 여러 투표의 참여 인원을 한 번에. 목록에서 투표 수만큼 조회가 나가지 않게 한다. */
    List<TravelPlanPollVotedCount> countVotedMembersByPollIds(
            @Param("pollIds") List<Long> pollIds);

    /**
     * 선택지별 표 수. 참여 인원과 같은 기준(ACTIVE)으로 센다.
     * 표를 하나도 못 받은 선택지는 결과에 나오지 않으므로 호출한 쪽이 0 으로 채운다.
     */
    List<TravelPlanPollOptionVoteCount> countSelectionsByPollIds(
            @Param("pollIds") List<Long> pollIds);

    /**
     * 이 투표에 표가 하나라도 있는지.
     * 첫 표가 나온 뒤에는 투표의 핵심 설정을 바꿀 수 없다는 정책에서 쓰게 된다.
     */
    boolean hasAnyVote(@Param("pollId") Long pollId);

    // ── 마감 ────────────────────────────────────────────────

    /**
     * 투표를 마감한다.
     *
     * <p>아직 진행 중일 때만 반영된다(status = 'OPEN' 조건).
     * 직접 마감과 전원 투표가 동시에 닿아도 한 번만 성공한다.
     *
     * @return 1 이면 방금 이 호출이 마감했고, 0 이면 이미 마감돼 있었다.
     */
    int closePoll(@Param("id") Long id,
                  @Param("closeReason") TravelPlanPollCloseReason closeReason);

    // ── 지우기 ──────────────────────────────────────────────

    /**
     * 투표를 지운다. 방 소속 조건을 함께 걸어 다른 방의 투표를 지울 수 없게 한다.
     *
     * <p>선택지·투표·고른 선택은 FK 의 ON DELETE CASCADE 로 함께 사라진다.
     * (options.poll_id / votes.poll_id / selections.vote_id / selections.option_id)
     * 그래서 남는 것 없이 이 한 문장으로 끝난다.
     */
    int deletePoll(@Param("id") Long id, @Param("travelPlanId") Long travelPlanId);

    /**
     * 방을 떠난 사람이 진행 중인 투표에 넣어 둔 표를 걷어 낸다.
     * 고른 선택은 selections.vote_id CASCADE 로 함께 사라진다.
     *
     * <p>이미 끝난 투표는 건드리지 않는다. 그때의 결과는 그대로 남아야 한다.
     *
     * @return 걷어 낸 표 수
     */
    int deleteVotesOfMemberInOpenPolls(@Param("travelPlanId") Long travelPlanId,
                                       @Param("memberId") Long memberId);

    /** 이 방에서 진행 중인 투표의 번호. 사람이 빠진 뒤 전원 투표 여부를 다시 볼 때 쓴다. */
    List<Long> findOpenPollIds(@Param("travelPlanId") Long travelPlanId);
}
