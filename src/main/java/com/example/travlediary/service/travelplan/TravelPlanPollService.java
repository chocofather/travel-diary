package com.example.travlediary.service.travelplan;

import com.example.travlediary.dto.TravelPlanPollCreateForm;
import com.example.travlediary.dto.TravelPlanPollDetailDto;
import com.example.travlediary.dto.TravelPlanPollDto;
import com.example.travlediary.dto.TravelPlanPollEventDto;
import com.example.travlediary.dto.TravelPlanPollOptionResultDto;
import com.example.travlediary.dto.TravelPlanPollSummaryDto;
import com.example.travlediary.model.TravelPlanMember;
import com.example.travlediary.model.TravelPlanPoll;
import com.example.travlediary.model.TravelPlanPollCloseType;
import com.example.travlediary.model.TravelPlanPollOption;
import com.example.travlediary.model.TravelPlanPollOptionVoteCount;
import com.example.travlediary.model.TravelPlanPollResultVisibility;
import com.example.travlediary.model.TravelPlanPollSelectionType;
import com.example.travlediary.model.TravelPlanPollStatus;
import com.example.travlediary.model.TravelPlanPollVote;
import com.example.travlediary.model.TravelPlanPollVotedCount;
import com.example.travlediary.repository.travelplan.TravelPlanMapper;
import com.example.travlediary.repository.travelplan.TravelPlanPollMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 방 투표 만들기.
 *
 * <p>투표와 채팅은 UX 상 같은 창에서 만들어지지만 표는 따로 둔다.
 * 투표를 만들 때 채팅 메시지 행을 함께 만들지 않는다.
 *
 * <p>만든 사람은 언제나 서버가 정한다. 클라이언트가 보낸 memberId 는 쓰지 않는다.
 * 투표와 선택지는 한 트랜잭션에 들어가 선택지 하나라도 실패하면 투표도 남지 않는다.
 */
@Service
@RequiredArgsConstructor
public class TravelPlanPollService {

    /** 선택지는 최소 두 개여야 고를 것이 생긴다. */
    public static final int MIN_OPTIONS = 2;
    /** DB 제한이 아니라 화면과 운영을 감당할 수 있는 선에서 정한 상한이다. */
    public static final int MAX_OPTIONS = 10;
    /** travel_plan_polls.title 이 varchar(200) 이다. */
    public static final int MAX_QUESTION_LENGTH = 200;
    /**
     * travel_plan_poll_options.content 는 TEXT 라 DB 상한이 훨씬 크지만,
     * 카드 한 줄로 보여 줄 수 있는 선에서 끊는다.
     */
    public static final int MAX_OPTION_LENGTH = 200;

    /** 아무도 고르지 않은 채 끝난 투표. */
    static final String NO_RESULT = "투표 결과 없음";
    /** 이름을 알 수 없는 경우. 실제로는 멤버 행을 지우지 않아 거의 나오지 않는다. */
    private static final String UNKNOWN_CREATOR = "알 수 없음";

    private final TravelPlanPollMapper travelPlanPollMapper;
    private final TravelPlanMapper travelPlanMapper;
    private final TravelPlanRoomAccess travelPlanRoomAccess;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 투표 1건과 선택지 전부를 한 번에 저장한다.
     *
     * <p>이번 단계에서 고르게 하지 않는 값은 기본값으로만 넣는다.
     * 진행 중(OPEN) / 결과 바로 공개(REALTIME) / 직접 마감(MANUAL) / 마감 시각 없음.
     */
    @Transactional
    public TravelPlanPollDto createPoll(Principal principal, Long travelPlanId,
                                        TravelPlanPollCreateForm form) {
        TravelPlanMember member = requireActiveMember(principal, travelPlanId);

        String question = requireQuestion(form == null ? null : form.getQuestion());
        TravelPlanPollSelectionType selectionType =
                requireSelectionType(form == null ? null : form.getSelectionType());
        List<String> options = requireOptions(form == null ? null : form.getOptions());

        TravelPlanPoll poll = new TravelPlanPoll();
        poll.setTravelPlanId(travelPlanId);
        poll.setCreatedByMemberId(member.getId());
        poll.setTitle(question);
        poll.setSelectionType(selectionType);
        // 아래 세 가지는 이번 단계의 만들기 화면에 없다. 기본값으로만 저장한다.
        poll.setResultVisibility(TravelPlanPollResultVisibility.REALTIME);
        poll.setCloseType(TravelPlanPollCloseType.MANUAL);
        poll.setDeadlineAt(null);
        poll.setStatus(TravelPlanPollStatus.OPEN);

        if (travelPlanPollMapper.insertPoll(poll) != 1 || poll.getId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "투표를 저장하지 못했습니다.");
        }

        List<TravelPlanPollOption> saved = new ArrayList<>();
        int displayOrder = 1;
        for (String content : options) {
            TravelPlanPollOption option = new TravelPlanPollOption();
            option.setPollId(poll.getId());
            option.setContent(content);
            option.setDisplayOrder(displayOrder++);
            // 하나라도 실패하면 투표만 남지 않도록 여기서 끊어 전체를 되돌린다.
            if (travelPlanPollMapper.insertOption(option) != 1) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "선택지를 저장하지 못했습니다.");
            }
            saved.add(option);
        }

        travelPlanMapper.touchLastActivity(travelPlanId);

        // 이름은 방금 확인한 참여 정보 그대로다. 다시 읽어 오지 않는다.
        poll.setCreatedByDisplayName(member.getDisplayName());
        TravelPlanPollDto dto = TravelPlanPollDto.of(poll, saved);
        // 만든 사람에게도 이 알림으로 도착한다.
        eventPublisher.publishEvent(new TravelPlanPollChangedEvent(
                travelPlanId, TravelPlanPollEventDto.created(dto)));
        return dto;
    }

    /**
     * 투표 센터의 [진행 중].
     * 목록에서는 선택지를 펼치지 않는다. 질문·만든 사람·참여 인원까지다.
     */
    @Transactional(readOnly = true)
    public List<TravelPlanPollSummaryDto> openPolls(Principal principal, Long travelPlanId) {
        requireActiveMember(principal, travelPlanId);
        return toSummaries(travelPlanId, travelPlanPollMapper.findOpenPolls(travelPlanId), false);
    }

    /**
     * 투표 센터의 [지난 투표]. 여기서는 참여 인원 대신 최종 결과만 보여 준다.
     * 마감 기능이 아직 없어 지금은 대개 비어 있다. 없는 것을 지어내지 않는다.
     */
    @Transactional(readOnly = true)
    public List<TravelPlanPollSummaryDto> closedPolls(Principal principal, Long travelPlanId) {
        requireActiveMember(principal, travelPlanId);
        return toSummaries(travelPlanId, travelPlanPollMapper.findClosedPolls(travelPlanId), true);
    }

    /**
     * 투표 상세. 선택지와 지금까지의 표, 그리고 내가 고른 것이 함께 온다.
     * 누가 무엇을 골랐는지는 담지 않는다.
     */
    @Transactional(readOnly = true)
    public TravelPlanPollDetailDto pollDetail(Principal principal, Long travelPlanId,
                                              Long pollId) {
        TravelPlanMember member = requireActiveMember(principal, travelPlanId);
        TravelPlanPoll poll = requirePollOfPlan(travelPlanId, pollId);

        List<TravelPlanPollOption> options = travelPlanPollMapper.findOptionsByPollIds(
                List.of(pollId));
        Map<Long, Integer> voteCounts = voteCountsOf(List.of(pollId)).getOrDefault(
                pollId, Map.of());

        List<TravelPlanPollOptionResultDto> results = options.stream()
                // 표를 하나도 못 받은 선택지는 집계에 없다. 0 으로 채운다.
                .map(option -> new TravelPlanPollOptionResultDto(option.getId(),
                        option.getContent(), voteCounts.getOrDefault(option.getId(), 0)))
                .toList();

        TravelPlanPollVote myVote = travelPlanPollMapper.findVoteByPollAndMember(
                pollId, member.getId());
        List<Long> selected = myVote == null
                ? List.of()
                : travelPlanPollMapper.findSelectedOptionIds(myVote.getId());

        return new TravelPlanPollDetailDto(
                poll.getId(),
                poll.getTitle(),
                displayNameOf(poll),
                poll.getSelectionType() == null ? null : poll.getSelectionType().name(),
                poll.getStatus() == null ? null : poll.getStatus().name(),
                travelPlanPollMapper.countVotedMembers(pollId),
                travelPlanMapper.countActiveMembers(travelPlanId),
                TravelPlanPollStatus.CLOSED.equals(poll.getStatus())
                        ? winnerSummaryOf(results)
                        : null,
                results,
                selected);
    }

    /**
     * 투표하기.
     *
     * <p>사람마다 투표 줄은 하나다((poll_id, member_id) UNIQUE).
     * 선택을 바꿔도 그 줄을 새로 만들지 않고 딸린 선택만 갈아 끼우므로,
     * 참여 인원은 그대로 있고 표만 옮겨 간다.
     */
    @Transactional
    public void submitVote(Principal principal, Long travelPlanId, Long pollId,
                           List<Long> optionIds) {
        TravelPlanMember member = requireActiveMember(principal, travelPlanId);
        TravelPlanPoll poll = requirePollOfPlan(travelPlanId, pollId);
        if (!TravelPlanPollStatus.OPEN.equals(poll.getStatus())) {
            throw new TravelPlanValidationException("poll", "이미 끝난 투표입니다.");
        }

        List<Long> chosen = requireSelection(poll, pollId, optionIds);

        TravelPlanPollVote vote = travelPlanPollMapper.findVoteByPollAndMember(
                pollId, member.getId());
        if (vote == null) {
            vote = new TravelPlanPollVote();
            vote.setPollId(pollId);
            vote.setMemberId(member.getId());
            if (travelPlanPollMapper.insertVote(vote) != 1 || vote.getId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "투표를 저장하지 못했습니다.");
            }
        } else {
            // 다시 고른 것이라 이전 선택을 걷어 낸다. 투표 줄은 그대로 둔다.
            travelPlanPollMapper.deleteSelectionsByVoteId(vote.getId());
        }

        for (Long optionId : chosen) {
            if (travelPlanPollMapper.insertSelection(vote.getId(), optionId) != 1) {
                // 여기서 끊으면 걷어 낸 이전 선택까지 함께 되돌아간다.
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "투표를 저장하지 못했습니다.");
            }
        }

        travelPlanMapper.touchLastActivity(travelPlanId);
        eventPublisher.publishEvent(new TravelPlanPollChangedEvent(
                travelPlanId, TravelPlanPollEventDto.voted(pollId)));
    }

    /** 목록 한 줄들. 투표 수만큼 조회가 나가지 않도록 집계를 한 번에 읽는다. */
    private List<TravelPlanPollSummaryDto> toSummaries(Long travelPlanId,
                                                       List<TravelPlanPoll> polls,
                                                       boolean withWinner) {
        if (polls.isEmpty()) {
            return List.of();
        }
        List<Long> pollIds = polls.stream().map(TravelPlanPoll::getId).toList();
        int activeMembers = travelPlanMapper.countActiveMembers(travelPlanId);

        Map<Long, Integer> votedCounts =
                travelPlanPollMapper.countVotedMembersByPollIds(pollIds).stream()
                        .collect(Collectors.toMap(TravelPlanPollVotedCount::getPollId,
                                TravelPlanPollVotedCount::getVotedMemberCount));

        // 끝난 투표만 결과를 계산한다. 진행 중 목록에는 표 수를 내보내지 않는다.
        Map<Long, Map<Long, Integer>> voteCounts =
                withWinner ? voteCountsOf(pollIds) : Map.of();
        Map<Long, List<TravelPlanPollOption>> optionsByPollId = withWinner
                ? travelPlanPollMapper.findOptionsByPollIds(pollIds).stream()
                        .collect(Collectors.groupingBy(TravelPlanPollOption::getPollId))
                : Map.of();

        return polls.stream()
                .map(poll -> new TravelPlanPollSummaryDto(
                        poll.getId(),
                        poll.getTitle(),
                        displayNameOf(poll),
                        poll.getStatus() == null ? null : poll.getStatus().name(),
                        votedCounts.getOrDefault(poll.getId(), 0),
                        activeMembers,
                        withWinner
                                ? winnerSummaryOf(resultsOf(
                                        optionsByPollId.getOrDefault(poll.getId(), List.of()),
                                        voteCounts.getOrDefault(poll.getId(), Map.of())))
                                : null))
                .toList();
    }

    private List<TravelPlanPollOptionResultDto> resultsOf(
            List<TravelPlanPollOption> options, Map<Long, Integer> voteCounts) {
        return options.stream()
                .map(option -> new TravelPlanPollOptionResultDto(option.getId(),
                        option.getContent(), voteCounts.getOrDefault(option.getId(), 0)))
                .toList();
    }

    /** poll -> (option -> 표 수). 표가 없는 선택지는 여기 없다. */
    private Map<Long, Map<Long, Integer>> voteCountsOf(List<Long> pollIds) {
        return travelPlanPollMapper.countSelectionsByPollIds(pollIds).stream()
                .collect(Collectors.groupingBy(
                        TravelPlanPollOptionVoteCount::getPollId,
                        Collectors.toMap(TravelPlanPollOptionVoteCount::getOptionId,
                                TravelPlanPollOptionVoteCount::getVoteCount)));
    }

    /**
     * 끝난 투표의 결과 한 줄.
     * 표가 같으면 하나를 임의로 고르지 않고 나란히 적는다.
     */
    private String winnerSummaryOf(List<TravelPlanPollOptionResultDto> results) {
        int best = results.stream()
                .mapToInt(TravelPlanPollOptionResultDto::voteCount)
                .max()
                .orElse(0);
        if (best == 0) {
            return NO_RESULT;
        }
        List<String> winners = results.stream()
                .filter(result -> result.voteCount() == best)
                .map(TravelPlanPollOptionResultDto::content)
                .toList();
        return winners.size() == 1
                ? winners.get(0)
                : String.join(" · ", winners) + " 공동 1위";
    }

    /**
     * 고른 선택지가 이 투표에서 받아들일 수 있는 것인지.
     * 화면에서 막는 것과 별개로 서버에서 다시 본다.
     */
    private List<Long> requireSelection(TravelPlanPoll poll, Long pollId, List<Long> optionIds) {
        // 같은 것을 두 번 보내도 한 번으로 본다.
        List<Long> chosen = (optionIds == null ? List.<Long>of() : optionIds).stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        if (chosen.isEmpty()) {
            throw new TravelPlanValidationException("optionIds", "선택지를 골라 주세요.");
        }
        if (TravelPlanPollSelectionType.SINGLE.equals(poll.getSelectionType())
                && chosen.size() != 1) {
            throw new TravelPlanValidationException("optionIds", "하나만 고를 수 있는 투표입니다.");
        }
        if (chosen.size() > travelPlanPollMapper.countOptionsByPollId(pollId)) {
            throw new TravelPlanValidationException("optionIds", "선택지를 다시 확인해 주세요.");
        }
        // 다른 투표의 선택지가 하나라도 섞이면 통째로 받지 않는다.
        if (travelPlanPollMapper.countOwnedOptions(pollId, chosen) != chosen.size()) {
            throw new TravelPlanValidationException("optionIds", "선택지를 다시 확인해 주세요.");
        }
        return chosen;
    }

    /** 다른 방의 투표 번호를 보내도 여기서 걸린다. */
    private TravelPlanPoll requirePollOfPlan(Long travelPlanId, Long pollId) {
        if (pollId == null) {
            throw new AccessDeniedException("찾을 수 없는 투표입니다.");
        }
        TravelPlanPoll poll = travelPlanPollMapper.findByIdAndPlanId(pollId, travelPlanId);
        if (poll == null) {
            throw new AccessDeniedException("찾을 수 없는 투표입니다.");
        }
        return poll;
    }

    private String displayNameOf(TravelPlanPoll poll) {
        String displayName = poll.getCreatedByDisplayName();
        return displayName == null || displayName.isBlank() ? UNKNOWN_CREATOR : displayName;
    }

    private String requireQuestion(String question) {
        String normalized = question == null ? "" : question.strip();
        if (normalized.isEmpty()) {
            throw new TravelPlanValidationException("question", "투표 질문을 입력해 주세요.");
        }
        if (normalized.length() > MAX_QUESTION_LENGTH) {
            throw new TravelPlanValidationException("question",
                    "질문은 " + MAX_QUESTION_LENGTH + "자까지 입력할 수 있습니다.");
        }
        return normalized;
    }

    /** 화면이 보낸 값이 정말 허용된 방식인지 본다. 모르는 값이면 받지 않는다. */
    private TravelPlanPollSelectionType requireSelectionType(String selectionType) {
        String normalized = selectionType == null
                ? "" : selectionType.strip().toUpperCase(Locale.ROOT);
        try {
            return TravelPlanPollSelectionType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new TravelPlanValidationException("selectionType", "선택 방식을 확인해 주세요.");
        }
    }

    /**
     * 빈 줄은 덜어 내고 남은 것만 본다.
     * 화면에서 줄을 지우지 않고 비워 두었을 수 있어, 비어 있다고 오류로 보지 않는다.
     */
    private List<String> requireOptions(List<String> options) {
        List<String> normalized = (options == null ? List.<String>of() : options).stream()
                .map(option -> option == null ? "" : option.strip())
                .filter(option -> !option.isEmpty())
                .toList();

        if (normalized.size() < MIN_OPTIONS) {
            throw new TravelPlanValidationException("options",
                    "선택지를 " + MIN_OPTIONS + "개 이상 입력해 주세요.");
        }
        if (normalized.size() > MAX_OPTIONS) {
            throw new TravelPlanValidationException("options",
                    "선택지는 " + MAX_OPTIONS + "개까지 만들 수 있습니다.");
        }
        if (normalized.stream().anyMatch(option -> option.length() > MAX_OPTION_LENGTH)) {
            throw new TravelPlanValidationException("options",
                    "선택지는 " + MAX_OPTION_LENGTH + "자까지 입력할 수 있습니다.");
        }
        // 같은 선택지가 두 개면 어느 쪽을 고른 것인지 알 수 없다.
        Set<String> unique = new LinkedHashSet<>(normalized);
        if (unique.size() != normalized.size()) {
            throw new TravelPlanValidationException("options", "같은 선택지를 두 번 넣을 수 없습니다.");
        }
        return normalized;
    }

    /**
     * 지금 이 사람이 이 방에서 투표를 만들 수 있는지.
     * OWNER 와 MEMBER 가 같고, 끝난 방과 LEFT / REMOVED / 비참여자는 여기서 걸린다.
     */
    private TravelPlanMember requireActiveMember(Principal principal, Long travelPlanId) {
        return travelPlanRoomAccess.findActiveMember(principal, travelPlanId)
                .orElseThrow(() -> new AccessDeniedException("여행계획에 참여 중이 아닙니다."));
    }
}
