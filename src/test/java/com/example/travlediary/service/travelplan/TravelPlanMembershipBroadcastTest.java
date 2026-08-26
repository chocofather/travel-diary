package com.example.travlediary.service.travelplan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 명단이 바뀌었다는 알림이 방으로 나가는 길.
 *
 * <p>알림에는 사람 수도 이름도 싣지 않는다.
 * 받은 화면이 서버에서 최신 명단을 다시 읽게 해,
 * 같은 알림을 두 번 받아도 숫자가 두 번 늘지 않게 하기 위해서다.
 */
@ExtendWith(MockitoExtension.class)
class TravelPlanMembershipBroadcastTest {

    private static final Long PLAN_ID = 42L;

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;
    @InjectMocks
    private TravelPlanMembershipChangedListener listener;

    @Test
    void theNoticeGoesToThatRoomAndNoOther() {
        listener.onMembershipChanged(new TravelPlanMembershipChangedEvent(PLAN_ID));

        verify(simpMessagingTemplate).convertAndSend(
                "/topic/travel-plans/42/members",
                Map.of("type", "MEMBERSHIP_CHANGED", "travelPlanId", PLAN_ID));
    }

    @Test
    void theNoticeIsSeparateFromWhoIsOnlineRightNow() {
        /*
          "참여자 N/8" 과 "접속 중 N명" 은 서로 다른 값이다.
          같은 topic 을 쓰면 한쪽 숫자가 다른 쪽 자리에 들어간다.
        */
        assertThat(TravelPlanMemberDestinations.topic(PLAN_ID))
                .isNotEqualTo(TravelPlanPresenceDestinations.topic(PLAN_ID));
        // 접속 표시 topic 을 명단 topic 으로 잘못 읽지 않는다
        assertThat(TravelPlanMemberDestinations.travelPlanIdOf(
                TravelPlanPresenceDestinations.topic(PLAN_ID))).isNull();
    }

    @Test
    void theNoticeCarriesNoHeadcountOfItsOwn() {
        listener.onMembershipChanged(new TravelPlanMembershipChangedEvent(PLAN_ID));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(simpMessagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.anyString(), captor.capture());
        // 방 번호와 종류뿐이다. 사람 수는 화면이 서버에서 다시 읽는다
        Map<String, Object> payload = (Map<String, Object>) captor.getValue();
        assertThat(payload).containsOnlyKeys("type", "travelPlanId");
    }

    @Test
    void thesameNoticeTwiceStillJustMeansReadItAgain() {
        // 알림에 사람 수가 없으므로 두 번 받아도 더해질 것이 없다
        listener.onMembershipChanged(new TravelPlanMembershipChangedEvent(PLAN_ID));
        listener.onMembershipChanged(new TravelPlanMembershipChangedEvent(PLAN_ID));

        verify(simpMessagingTemplate, times(2)).convertAndSend(
                "/topic/travel-plans/42/members",
                Map.of("type", "MEMBERSHIP_CHANGED", "travelPlanId", PLAN_ID));
    }

    @Test
    void nothingIsAnnouncedUntilTheChangeIsActuallySaved() throws NoSuchMethodException {
        // 되돌아간 변경 때문에 다른 화면에 없는 사람이 남으면 안 된다
        Method method = TravelPlanMembershipChangedListener.class.getDeclaredMethod(
                "onMembershipChanged", TravelPlanMembershipChangedEvent.class);

        TransactionalEventListener annotation =
                method.getAnnotation(TransactionalEventListener.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
