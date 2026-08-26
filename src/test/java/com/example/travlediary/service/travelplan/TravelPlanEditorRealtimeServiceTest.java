package com.example.travlediary.service.travelplan;

import com.example.travlediary.service.travelplan.TravelPlanEditorRealtimeService.EditorLock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 누가 어느 자리를 붙잡고 무엇을 쓰고 있는지만 세는 메모리 장부.
 * 자리를 잡는 단위는 사람이 아니라 연결(session)이다.
 */
class TravelPlanEditorRealtimeServiceTest {

    private static final Long PLAN_ID = 42L;
    private static final Long OTHER_PLAN_ID = 43L;
    private static final Long DAY_ID = 100L;
    private static final Long ITEM_ID = 500L;
    private static final Long OTHER_ITEM_ID = 501L;

    private final TravelPlanEditorRealtimeService editor = new TravelPlanEditorRealtimeService();

    // ── 자리 잡기 ───────────────────────────────────────────

    @Test
    void oneSessionTakesTheSpotAndTheNextOneCannot() {
        assertThat(editor.tryAcquire(editLock("session-A", 11L, "민준"))).isPresent();

        // 같은 일정은 한 사람만 고칠 수 있다
        assertThat(editor.tryAcquire(editLock("session-B", 12L, "쭈니"))).isEmpty();
    }

    @Test
    void theSpotIsFreeAgainOnceItIsReleased() {
        editor.tryAcquire(editLock("session-A", 11L, "민준"));

        assertThat(editor.release(PLAN_ID, TravelPlanEditorRealtimeService.editLockKey(ITEM_ID),
                "session-A")).isPresent();
        assertThat(editor.tryAcquire(editLock("session-B", 12L, "쭈니"))).isPresent();
    }

    @Test
    void theNewItemSpotIsOnePerDay() {
        assertThat(editor.tryAcquire(addLock("session-A", 11L, "민준"))).isPresent();
        assertThat(editor.tryAcquire(addLock("session-B", 12L, "쭈니"))).isEmpty();
    }

    @Test
    void differentItemsInTheSameDayCanBeEditedAtTheSameTime() {
        assertThat(editor.tryAcquire(editLock("session-A", 11L, "민준"))).isPresent();

        EditorLock other = new EditorLock(PLAN_ID,
                TravelPlanEditorRealtimeService.editLockKey(OTHER_ITEM_ID), "session-B",
                TravelPlanEditorRealtimeService.EDIT_MODE,
                DAY_ID, OTHER_ITEM_ID, null, 12L, "쭈니", "", "");
        assertThat(editor.tryAcquire(other)).isPresent();
        assertThat(editor.locksOf(PLAN_ID)).hasSize(2);
    }

    @Test
    void anotherTabOfTheSamePersonStillCannotTakeAHeldSpot() {
        // 자리는 사람이 아니라 연결 단위다
        editor.tryAcquire(editLock("tab-1", 11L, "민준"));

        assertThat(editor.tryAcquire(editLock("tab-2", 11L, "민준"))).isEmpty();

        // 첫 탭이 닫히면 다른 탭이 가져갈 수 있다
        editor.releaseAllBySession("tab-1");
        assertThat(editor.tryAcquire(editLock("tab-2", 11L, "민준"))).isPresent();
    }

    @Test
    void onlyTheHolderCanLetGo() {
        editor.tryAcquire(editLock("session-A", 11L, "민준"));

        assertThat(editor.release(PLAN_ID,
                TravelPlanEditorRealtimeService.editLockKey(ITEM_ID), "session-B")).isEmpty();
        assertThat(editor.find(PLAN_ID,
                TravelPlanEditorRealtimeService.editLockKey(ITEM_ID))).isPresent();
    }

    @Test
    void exactlyOneOfManySimultaneousClicksWins() throws InterruptedException {
        int attempts = 64;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(attempts);
        AtomicInteger granted = new AtomicInteger();

        for (int index = 0; index < attempts; index++) {
            String sessionId = "session-" + index;
            pool.submit(() -> {
                try {
                    ready.await();
                    if (editor.tryAcquire(editLock(sessionId, 11L, "민준")).isPresent()) {
                        granted.incrementAndGet();
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(granted.get()).isEqualTo(1);
        assertThat(editor.locksOf(PLAN_ID)).hasSize(1);
    }

    // ── 대안(B/C) 자리 ─────────────────────────────────────

    @Test
    void theNewAlternativeSpotIsOnePerItem() {
        // B 인지 C 인지는 저장할 때 서버가 정하므로 새 대안 자리는 A 일정마다 하나뿐이다
        assertThat(editor.tryAcquire(alternativeAddLock("session-A", ITEM_ID, 11L, "민준")))
                .isPresent();
        assertThat(editor.tryAcquire(alternativeAddLock("session-B", ITEM_ID, 12L, "쭈니")))
                .isEmpty();

        // 다른 A 일정 밑이라면 따로다
        assertThat(editor.tryAcquire(alternativeAddLock("session-B", OTHER_ITEM_ID, 12L, "쭈니")))
                .isPresent();
    }

    @Test
    void bAndCAreHeldSeparately() {
        assertThat(editor.tryAcquire(alternativeEditLock("session-A", ITEM_ID, 900L, 11L, "민준")))
                .isPresent();
        assertThat(editor.tryAcquire(alternativeEditLock("session-B", ITEM_ID, 901L, 12L, "쭈니")))
                .isPresent();

        // 같은 대안은 한 사람만 고칠 수 있다
        assertThat(editor.tryAcquire(alternativeEditLock("session-C", ITEM_ID, 900L, 13L, "지우")))
                .isEmpty();
    }

    @Test
    void anItemAndItsAlternativeDoNotShareASpot() {
        // A 를 고치는 사람이 있어도 그 밑의 대안은 다른 사람이 쓸 수 있다
        assertThat(editor.tryAcquire(editLock("session-A", 11L, "민준"))).isPresent();
        assertThat(editor.tryAcquire(alternativeAddLock("session-B", ITEM_ID, 12L, "쭈니")))
                .isPresent();
        assertThat(editor.tryAcquire(alternativeEditLock("session-C", ITEM_ID, 900L, 13L, "지우")))
                .isPresent();
        assertThat(editor.locksOf(PLAN_ID)).hasSize(3);
    }

    @Test
    void movingFromAnItemToAnAlternativeLetsGoOfTheItem() {
        // 화면 정책이 "한 번에 편집기 하나" 라 A 와 B/C 가 동시에 열리지 않는다
        editor.tryAcquire(editLock("session-A", 11L, "민준"));
        editor.tryAcquire(alternativeAddLock("session-A", ITEM_ID, 11L, "민준"));

        assertThat(editor.locksOf(PLAN_ID))
                .extracting(dto -> dto.lockKey())
                .containsExactly(
                        TravelPlanEditorRealtimeService.alternativeAddLockKey(ITEM_ID));
    }

    @Test
    void anAlternativeCarriesItsConditionAndContentTogether() {
        editor.tryAcquire(alternativeAddLock("session-A", ITEM_ID, 11L, "민준"));
        String key = TravelPlanEditorRealtimeService.alternativeAddLockKey(ITEM_ID);

        Optional<EditorLock> latest =
                editor.updateDraft(PLAN_ID, key, "session-A", "비가 많이 올 때", "아쿠아플라넷");

        assertThat(latest).isPresent();
        assertThat(latest.get().conditionLabel()).isEqualTo("비가 많이 올 때");
        assertThat(latest.get().content()).isEqualTo("아쿠아플라넷");

        // 밖으로 나가는 값에도 두 칸이 함께 실린다
        assertThat(editor.locksOf(PLAN_ID).get(0).conditionLabel()).isEqualTo("비가 많이 올 때");
        assertThat(editor.locksOf(PLAN_ID).get(0).content()).isEqualTo("아쿠아플라넷");
    }

    @Test
    void whatGoesOutSaysWhichAlternativeItIs() {
        editor.tryAcquire(alternativeEditLock("session-A", ITEM_ID, 900L, 11L, "민준"));

        assertThat(editor.locksOf(PLAN_ID).get(0).alternativeId()).isEqualTo(900L);
        assertThat(editor.locksOf(PLAN_ID).get(0).mode())
                .isEqualTo(TravelPlanEditorRealtimeService.ALT_EDIT_MODE);
    }

    @Test
    void aClosedTabLetsGoOfTheAlternativeToo() {
        editor.tryAcquire(alternativeEditLock("session-A", ITEM_ID, 900L, 11L, "민준"));

        assertThat(editor.releaseAllBySession("session-A"))
                .extracting(EditorLock::lockKey)
                .containsExactly(TravelPlanEditorRealtimeService.alternativeEditLockKey(900L));
        assertThat(editor.locksOf(PLAN_ID)).isEmpty();
    }

    // ── 자리를 놓는 경우는 정해져 있다 ──────────────────────

    @Test
    void anItemSpotSurvivesTheUserLookingAtAnotherWindow() {
        // 창을 옮기는 것은 서버까지 오지 않는 화면 안의 일이다.
        // 그 사이에도 자리는 그대로라 다른 계정이 같은 일정을 열 수 없다.
        editor.tryAcquire(editLock("session-A", 11L, "민준"));
        editor.updateDraft(PLAN_ID, TravelPlanEditorRealtimeService.editLockKey(ITEM_ID),
                "session-A", "", "경복궁");

        assertThat(editor.tryAcquire(editLock("session-B", 12L, "쭈니"))).isEmpty();
        assertThat(editor.find(PLAN_ID, TravelPlanEditorRealtimeService.editLockKey(ITEM_ID))
                .orElseThrow().content()).isEqualTo("경복궁");
    }

    @Test
    void anAlternativeSpotSurvivesItToo() {
        editor.tryAcquire(alternativeEditLock("session-A", ITEM_ID, 900L, 11L, "민준"));
        editor.updateDraft(PLAN_ID,
                TravelPlanEditorRealtimeService.alternativeEditLockKey(900L),
                "session-A", "비가 많이 올 때", "아쿠아플라넷");

        assertThat(editor.tryAcquire(alternativeEditLock("session-B", ITEM_ID, 900L, 12L, "쭈니")))
                .isEmpty();
        assertThat(editor.find(PLAN_ID,
                TravelPlanEditorRealtimeService.alternativeEditLockKey(900L))
                .orElseThrow().conditionLabel()).isEqualTo("비가 많이 올 때");
    }

    @Test
    void cancellingIsWhatLetsGoOfTheSpot() {
        // Esc / 취소 버튼 / 저장 성공이 부르는 것은 모두 이 한 경로다
        editor.tryAcquire(editLock("session-A", 11L, "민준"));

        assertThat(editor.release(PLAN_ID,
                TravelPlanEditorRealtimeService.editLockKey(ITEM_ID), "session-A")).isPresent();
        assertThat(editor.locksOf(PLAN_ID)).isEmpty();
        assertThat(editor.tryAcquire(editLock("session-B", 12L, "쭈니"))).isPresent();
    }

    @Test
    void aLostConnectionIsTheOtherWayASpotIsLetGo() {
        editor.tryAcquire(alternativeEditLock("session-A", ITEM_ID, 900L, 11L, "민준"));

        assertThat(editor.releaseAllBySession("session-A")).hasSize(1);
        assertThat(editor.locksOf(PLAN_ID)).isEmpty();
        assertThat(editor.tryAcquire(alternativeEditLock("session-B", ITEM_ID, 900L, 12L, "쭈니")))
                .isPresent();
    }

    // ── 작성 중 내용 ────────────────────────────────────────

    @Test
    void theLatestTypingReplacesTheOne() {
        editor.tryAcquire(addLock("session-A", 11L, "민준"));
        String key = TravelPlanEditorRealtimeService.addLockKey(DAY_ID);

        editor.updateDraft(PLAN_ID, key, "session-A", "", "경");
        editor.updateDraft(PLAN_ID, key, "session-A", "", "경복");
        Optional<EditorLock> latest = editor.updateDraft(PLAN_ID, key, "session-A", "", "경복궁");

        assertThat(latest).isPresent();
        assertThat(latest.get().content()).isEqualTo("경복궁");
        assertThat(editor.locksOf(PLAN_ID).get(0).content()).isEqualTo("경복궁");
    }

    @Test
    void nobodyCanTypeIntoSomeoneElsesSpot() {
        editor.tryAcquire(editLock("session-A", 11L, "민준"));
        String key = TravelPlanEditorRealtimeService.editLockKey(ITEM_ID);

        assertThat(editor.updateDraft(PLAN_ID, key, "session-B", "", "몰래 수정")).isEmpty();
        assertThat(editor.find(PLAN_ID, key).orElseThrow().content()).isEmpty();
    }

    @Test
    void typingWithoutHoldingTheSpotDoesNothing() {
        assertThat(editor.updateDraft(PLAN_ID,
                TravelPlanEditorRealtimeService.editLockKey(ITEM_ID), "session-A", "", "경복궁"))
                .isEmpty();
        assertThat(editor.locksOf(PLAN_ID)).isEmpty();
    }

    // ── 연결이 끊길 때 ──────────────────────────────────────

    @Test
    void aClosedTabLetsGoOfEverythingItHeld() {
        editor.tryAcquire(editLock("session-A", 11L, "민준"));
        editor.updateDraft(PLAN_ID, TravelPlanEditorRealtimeService.editLockKey(ITEM_ID),
                "session-A", "", "작성 중");

        List<EditorLock> released = editor.releaseAllBySession("session-A");

        assertThat(released).hasSize(1);
        assertThat(released.get(0).content()).isEqualTo("작성 중");
        assertThat(editor.locksOf(PLAN_ID)).isEmpty();
    }

    @Test
    void oneClosedTabDoesNotDisturbAnother() {
        editor.tryAcquire(editLock("session-A", 11L, "민준"));
        EditorLock other = new EditorLock(PLAN_ID,
                TravelPlanEditorRealtimeService.editLockKey(OTHER_ITEM_ID), "session-B",
                TravelPlanEditorRealtimeService.EDIT_MODE,
                DAY_ID, OTHER_ITEM_ID, null, 12L, "쭈니", "", "");
        editor.tryAcquire(other);

        editor.releaseAllBySession("session-A");

        assertThat(editor.locksOf(PLAN_ID))
                .extracting(dto -> dto.lockKey())
                .containsExactly(TravelPlanEditorRealtimeService.editLockKey(OTHER_ITEM_ID));
    }

    @Test
    void anUnknownDisconnectIsIgnoredRatherThanFailing() {
        assertThat(editor.releaseAllBySession("never-seen")).isEmpty();
        assertThat(editor.releaseAllBySession(null)).isEmpty();
        assertThat(editor.release(PLAN_ID, "ITEM:1", "nobody")).isEmpty();
    }

    // ── 방 사이 격리 ────────────────────────────────────────

    @Test
    void roomsNeverShareSpots() {
        editor.tryAcquire(editLock("session-A", 11L, "민준"));
        EditorLock elsewhere = new EditorLock(OTHER_PLAN_ID,
                TravelPlanEditorRealtimeService.editLockKey(ITEM_ID), "session-B",
                TravelPlanEditorRealtimeService.EDIT_MODE,
                DAY_ID, ITEM_ID, null, 12L, "쭈니", "", "");

        // 다른 방이라면 같은 이름의 자리도 따로다
        assertThat(editor.tryAcquire(elsewhere)).isPresent();
        assertThat(editor.locksOf(PLAN_ID)).hasSize(1);
        assertThat(editor.locksOf(OTHER_PLAN_ID)).hasSize(1);
    }

    @Test
    void anEmptyRoomHasNothingHeld() {
        assertThat(editor.locksOf(PLAN_ID)).isEmpty();
        assertThat(editor.locksOf(null)).isEmpty();
        assertThat(editor.find(PLAN_ID, "ITEM:1")).isEmpty();
    }

    @Test
    void whatGoesOutCarriesNoAccountInformation() {
        editor.tryAcquire(editLock("session-A", 11L, "민준"));

        assertThat(editor.locksOf(PLAN_ID).get(0).toString())
                .contains("민준")
                .doesNotContain("session-A")
                .doesNotContain("userId")
                .doesNotContain("@");
    }

    @Test
    void oneConnectionOnlyEverHoldsOneSpot() {
        // 화면 정책이 "한 번에 편집기 하나" 이므로 서버도 같은 약속을 지킨다.
        // 첫 자리에서 편집기가 열리지 못한 채 다음 자리를 잡아도 잠금이 쌓이면 안 된다.
        editor.tryAcquire(editLock("session-A", 11L, "민준"));

        EditorLock second = new EditorLock(PLAN_ID,
                TravelPlanEditorRealtimeService.editLockKey(OTHER_ITEM_ID), "session-A",
                TravelPlanEditorRealtimeService.EDIT_MODE,
                DAY_ID, OTHER_ITEM_ID, null, 11L, "민준", "", "");
        assertThat(editor.tryAcquire(second)).isPresent();

        // 먼저 잡았던 자리는 저절로 놓인다
        assertThat(editor.locksOf(PLAN_ID))
                .extracting(dto -> dto.lockKey())
                .containsExactly(TravelPlanEditorRealtimeService.editLockKey(OTHER_ITEM_ID));
    }

    @Test
    void takingANewSpotSaysWhichOneWasLetGo() {
        editor.tryAcquire(editLock("session-A", 11L, "민준"));

        // 놓인 자리도 방에 알려야 다른 화면의 "편집 중" 표시가 사라진다
        assertThat(editor.releasedWhenAcquiring(PLAN_ID, "session-A",
                TravelPlanEditorRealtimeService.addLockKey(DAY_ID)))
                .extracting(EditorLock::lockKey)
                .containsExactly(TravelPlanEditorRealtimeService.editLockKey(ITEM_ID));
    }

    @Test
    void anotherConnectionKeepsItsSpotWhenIMoveOn() {
        editor.tryAcquire(editLock("session-A", 11L, "민준"));
        EditorLock other = new EditorLock(PLAN_ID,
                TravelPlanEditorRealtimeService.editLockKey(OTHER_ITEM_ID), "session-B",
                TravelPlanEditorRealtimeService.EDIT_MODE,
                DAY_ID, OTHER_ITEM_ID, null, 12L, "쭈니", "", "");
        editor.tryAcquire(other);

        editor.tryAcquire(addLock("session-A", 11L, "민준"));

        // 내가 자리를 옮겨도 남의 자리는 그대로다
        assertThat(editor.locksOf(PLAN_ID))
                .extracting(dto -> dto.lockKey())
                .containsExactlyInAnyOrder(
                        TravelPlanEditorRealtimeService.editLockKey(OTHER_ITEM_ID),
                        TravelPlanEditorRealtimeService.addLockKey(DAY_ID));
    }

    @Test
    void nothingHereEverReachesTheDatabase() {
        // 작성 중 내용은 임시일 뿐이다. 저장소를 들고 있지 않고 메모리 표만 있다
        assertThat(java.util.Arrays.stream(
                        TravelPlanEditorRealtimeService.class.getDeclaredFields())
                .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                .map(java.lang.reflect.Field::getType))
                .isNotEmpty()
                .allMatch(java.util.Map.class::isAssignableFrom);

        editor.tryAcquire(addLock("session-A", 11L, "민준"));
        editor.updateDraft(PLAN_ID, TravelPlanEditorRealtimeService.addLockKey(DAY_ID),
                "session-A", "", "경복궁");

        // 서버가 다시 뜨면 비어 있는 것이 정상이다
        assertThat(new TravelPlanEditorRealtimeService().locksOf(PLAN_ID)).isEmpty();
    }

    private EditorLock editLock(String sessionId, Long memberId, String displayName) {
        return new EditorLock(PLAN_ID, TravelPlanEditorRealtimeService.editLockKey(ITEM_ID),
                sessionId, TravelPlanEditorRealtimeService.EDIT_MODE,
                DAY_ID, ITEM_ID, null, memberId, displayName, "", "");
    }

    private EditorLock addLock(String sessionId, Long memberId, String displayName) {
        return new EditorLock(PLAN_ID, TravelPlanEditorRealtimeService.addLockKey(DAY_ID),
                sessionId, TravelPlanEditorRealtimeService.ADD_MODE,
                DAY_ID, null, null, memberId, displayName, "", "");
    }

    /** 새 대안 자리. 그 A 일정마다 하나뿐이다(B 인지 C 인지는 저장할 때 정해진다). */
    private EditorLock alternativeAddLock(String sessionId, Long itemId,
                                          Long memberId, String displayName) {
        return new EditorLock(PLAN_ID,
                TravelPlanEditorRealtimeService.alternativeAddLockKey(itemId),
                sessionId, TravelPlanEditorRealtimeService.ALT_ADD_MODE,
                DAY_ID, itemId, null, memberId, displayName, "", "");
    }

    /** 이미 저장된 B/C 한 칸. */
    private EditorLock alternativeEditLock(String sessionId, Long itemId, Long alternativeId,
                                           Long memberId, String displayName) {
        return new EditorLock(PLAN_ID,
                TravelPlanEditorRealtimeService.alternativeEditLockKey(alternativeId),
                sessionId, TravelPlanEditorRealtimeService.ALT_EDIT_MODE,
                DAY_ID, itemId, alternativeId, memberId, displayName, "", "");
    }
}
