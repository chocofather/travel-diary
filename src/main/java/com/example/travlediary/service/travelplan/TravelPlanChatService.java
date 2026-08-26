package com.example.travlediary.service.travelplan;

import com.example.travlediary.dto.TravelPlanChatEventDto;
import com.example.travlediary.dto.TravelPlanChatMessageDto;
import com.example.travlediary.dto.TravelPlanChatTimelineDto;
import com.example.travlediary.dto.TravelPlanChatTimelineItemDto;
import com.example.travlediary.model.TravelPlanChatMessage;
import com.example.travlediary.model.TravelPlanChatMessageType;
import com.example.travlediary.model.TravelPlanMember;
import com.example.travlediary.model.TravelPlanPoll;
import com.example.travlediary.repository.travelplan.TravelPlanChatMapper;
import com.example.travlediary.repository.travelplan.TravelPlanPollMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 방 채팅.
 *
 * <p>메시지는 메모리에 두지 않는다. DB 가 대화 기록의 기준이고,
 * 커밋이 끝난 뒤에만 다른 화면으로 나간다.
 *
 * <p>보낸 사람은 언제나 서버가 정한다. 클라이언트가 보낸 memberId / displayName 은 쓰지 않는다.
 * 이미 연결돼 있다고 해서 권한이 유지되지도 않는다. 쓰기 동작마다 ACTIVE 참여자인지 다시 본다.
 */
@Service
@RequiredArgsConstructor
public class TravelPlanChatService {

    /** 채팅 패널을 처음 열 때와 [이전 메시지 보기] 한 번에 가져오는 개수. */
    public static final int PAGE_SIZE = 40;

    /**
     * 한 메시지의 최대 길이.
     * content 는 TEXT 라 DB 상한이 훨씬 크지만, 화면과 전송량이 감당할 만한 선에서 끊는다.
     */
    public static final int MAX_CONTENT_LENGTH = 2000;

    private final TravelPlanChatMapper travelPlanChatMapper;
    /** 채팅창에 끼워 넣을 "새 투표" 기록만 읽는다. 투표를 만들거나 고치지 않는다. */
    private final TravelPlanPollMapper travelPlanPollMapper;
    private final TravelPlanRoomAccess travelPlanRoomAccess;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 채팅창에 그릴 한 페이지.
     *
     * <p>대화와 "새 투표를 만들었어요" 알림을 시간 순서로 합쳐 돌려준다.
     * 합치는 것은 여기 읽기 모델에서만이고, DB 에서는 두 표가 계속 따로다.
     * 투표를 채팅 행으로 옮겨 적거나 표시용 문자열을 남기지 않는다.
     *
     * <p>표가 둘이라 번호 하나로는 자를 수 없다.
     * 각자의 번호로 따로 끊어 와서 합치고, 다음 기준도 각자 돌려준다.
     * 대화 번호만 보고 자르면 그 사이의 투표 알림이 영영 빠진다.
     *
     * @param beforeMessageId 둘 다 null 이면 가장 최근 페이지
     * @param beforePollId    둘 다 null 이면 가장 최근 페이지
     */
    @Transactional(readOnly = true)
    public TravelPlanChatTimelineDto timeline(Principal principal, Long travelPlanId,
                                              Long beforeMessageId, Long beforePollId) {
        requireActiveMember(principal, travelPlanId);
        boolean firstPage = beforeMessageId == null && beforePollId == null;

        List<TravelPlanChatMessage> messages = firstPage
                ? travelPlanChatMapper.findRecentMessages(travelPlanId, PAGE_SIZE)
                : beforeOrEmpty(beforeMessageId, before ->
                        travelPlanChatMapper.findMessagesBefore(travelPlanId, before, PAGE_SIZE));
        List<TravelPlanPoll> polls = firstPage
                ? travelPlanPollMapper.findRecentPolls(travelPlanId, PAGE_SIZE)
                : beforeOrEmpty(beforePollId, before ->
                        travelPlanPollMapper.findPollsBefore(travelPlanId, before, PAGE_SIZE));

        List<TravelPlanChatTimelineItemDto> items = new ArrayList<>();
        messages.stream().map(TravelPlanChatTimelineItemDto::ofMessage).forEach(items::add);
        polls.stream().map(TravelPlanChatTimelineItemDto::ofPollCreated).forEach(items::add);
        // 화면은 오래된 것이 위에 온다.
        items.sort(Comparator.comparing(
                TravelPlanChatTimelineItemDto::createdAt,
                Comparator.nullsFirst(Comparator.naturalOrder())));

        // 각자 한 페이지를 꽉 채워 왔다면 그 앞에 더 있을 수 있다.
        boolean moreMessages = messages.size() >= PAGE_SIZE;
        boolean morePolls = polls.size() >= PAGE_SIZE;
        return new TravelPlanChatTimelineDto(
                items,
                moreMessages || morePolls,
                // 가져온 것이 없으면 그 쪽은 여기서 끝이다. 기준을 되돌려 같은 자리를 맴돌지 않는다.
                lastIdOf(messages, TravelPlanChatMessage::getId),
                lastIdOf(polls, TravelPlanPoll::getId));
    }

    /**
     * 메시지 저장.
     * 보낸 사람은 지금 로그인한 사람의 ACTIVE 참여 정보에서만 나온다.
     */
    @Transactional
    public TravelPlanChatMessageDto sendMessage(Principal principal, Long travelPlanId,
                                                String content) {
        TravelPlanMember member = requireActiveMember(principal, travelPlanId);
        String normalized = requireContent(content);

        TravelPlanChatMessage message = new TravelPlanChatMessage();
        message.setTravelPlanId(travelPlanId);
        message.setSenderMemberId(member.getId());
        message.setMessageType(TravelPlanChatMessageType.USER);
        message.setContent(normalized);

        if (travelPlanChatMapper.insertMessage(message) != 1) {
            throw new TravelPlanValidationException("content", "메시지를 보내지 못했습니다.");
        }
        // 이름은 방금 확인한 참여 정보 그대로다. 다시 읽어 오지 않는다.
        message.setSenderDisplayName(member.getDisplayName());

        TravelPlanChatMessageDto dto = TravelPlanChatMessageDto.of(message);
        // 보낸 사람에게도 이 알림으로 도착한다. 화면에 미리 그려 두지 않는다.
        eventPublisher.publishEvent(new TravelPlanChatChangedEvent(
                travelPlanId, TravelPlanChatEventDto.created(dto)));
        return dto;
    }

    /**
     * 본인이 보낸 메시지 지움.
     * 행을 지우지 않고 지움 표시만 남겨 대화 문맥이 당겨지지 않게 한다.
     *
     * <p>방장이라고 남의 메시지를 지울 수는 없다. 조건이 SQL 안에 있어 한 건도 바뀌지 않는다.
     */
    @Transactional
    public void deleteMessage(Principal principal, Long travelPlanId, Long messageId) {
        TravelPlanMember member = requireActiveMember(principal, travelPlanId);
        if (messageId == null) {
            throw new AccessDeniedException("지울 수 없는 메시지입니다.");
        }

        if (travelPlanChatMapper.markMessageDeleted(messageId, travelPlanId, member.getId()) != 1) {
            // 남의 메시지인지 이미 지워진 것인지 구분해 알리지 않는다.
            // 이미 지워져 있었다면 화면은 이미 지움으로 보이고 있어 더 할 일이 없다.
            TravelPlanChatMessage existing =
                    travelPlanChatMapper.findByIdAndPlanId(messageId, travelPlanId);
            if (existing != null && member.getId().equals(existing.getSenderMemberId())) {
                return;
            }
            throw new AccessDeniedException("내가 보낸 메시지만 지울 수 있습니다.");
        }

        eventPublisher.publishEvent(new TravelPlanChatChangedEvent(
                travelPlanId, TravelPlanChatEventDto.deleted(messageId)));
    }

    /** 아직 읽지 않은 메시지 수. 내가 보낸 것과 지워진 것은 세지 않는다. */
    @Transactional(readOnly = true)
    public int unreadCount(Principal principal, Long travelPlanId) {
        TravelPlanMember member = requireActiveMember(principal, travelPlanId);
        return travelPlanChatMapper.countUnread(travelPlanId, member.getId(),
                travelPlanChatMapper.findLastReadMessageId(travelPlanId, member.getId()));
    }

    /**
     * 여기까지 읽었다고 표시한다.
     *
     * <p>읽음 위치가 실제로 앞으로 나아갈 때만 쓴다.
     * 스크롤할 때마다 같은 값을 다시 쓰지 않도록 이미 그 자리를 읽었으면 아무것도 하지 않는다.
     *
     * @param lastReadMessageId null 이면 이 방의 마지막 메시지까지 읽은 것으로 본다
     * @return 갱신 뒤의 안 읽은 개수
     */
    @Transactional
    public int markRead(Principal principal, Long travelPlanId, Long lastReadMessageId) {
        TravelPlanMember member = requireActiveMember(principal, travelPlanId);

        Long target = lastReadMessageId != null
                ? lastReadMessageId
                : travelPlanChatMapper.findLatestMessageId(travelPlanId);
        if (target == null) {
            // 아직 아무 대화도 없다.
            return 0;
        }

        Long current = travelPlanChatMapper.findLastReadMessageId(travelPlanId, member.getId());
        if (current == null || current < target) {
            travelPlanChatMapper.upsertReadPosition(travelPlanId, member.getId(), target);
            current = target;
        }
        return travelPlanChatMapper.countUnread(travelPlanId, member.getId(), current);
    }

    /** 기준이 없는 쪽은 이미 끝까지 본 것이다. 다시 읽지 않는다. */
    private <T> List<T> beforeOrEmpty(Long before, java.util.function.LongFunction<List<T>> read) {
        return before == null ? List.of() : read.apply(before);
    }

    /**
     * 이번에 읽어 온 것 중 가장 앞선 번호. 다음 페이지의 기준이 된다.
     * 최신순으로 오므로 마지막 것이 가장 오래된 것이다.
     */
    private <T> Long lastIdOf(List<T> newestFirst, java.util.function.Function<T, Long> idOf) {
        return newestFirst.isEmpty() ? null : idOf.apply(newestFirst.get(newestFirst.size() - 1));
    }

    /**
     * 보낼 수 있는 내용인지.
     * 줄바꿈은 그대로 두고 앞뒤 공백만 덜어 낸다. 공백만 보내는 것은 메시지로 보지 않는다.
     */
    private String requireContent(String content) {
        String normalized = content == null ? "" : content.strip();
        if (normalized.isEmpty()) {
            throw new TravelPlanValidationException("content", "메시지를 입력해 주세요.");
        }
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw new TravelPlanValidationException("content",
                    "메시지는 " + MAX_CONTENT_LENGTH + "자까지 보낼 수 있습니다.");
        }
        return normalized;
    }

    /**
     * 지금 이 사람이 이 방에서 채팅할 수 있는지.
     * 끝난 방과 LEFT / REMOVED / 비참여자는 여기서 걸린다.
     */
    private TravelPlanMember requireActiveMember(Principal principal, Long travelPlanId) {
        return travelPlanRoomAccess.findActiveMember(principal, travelPlanId)
                .orElseThrow(() -> new AccessDeniedException("여행계획에 참여 중이 아닙니다."));
    }
}
