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
                TravelPlanEditorRealtimeService.EDIT_MODE, DAY_ID, OTHER_ITEM_ID, 12L, "쭈니", "");
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

    // ── 작성 중 내용 ────────────────────────────────────────

    @Test
    void theLatestTypingReplacesTheOne() {
        editor.tryAcquire(addLock("session-A", 11L, "민준"));
        String key = TravelPlanEditorRealtimeService.addLockKey(DAY_ID);

        editor.updateDraft(PLAN_ID, key, "session-A", "경");
        editor.updateDraft(PLAN_ID, key, "session-A", "경복");
        Optional<EditorLock> latest = editor.updateDraft(PLAN_ID, key, "session-A", "경복궁");

        assertThat(latest).isPresent();
        assertThat(latest.get().content()).isEqualTo("경복궁");
        assertThat(editor.locksOf(PLAN_ID).get(0).content()).isEqualTo("경복궁");
    }

    @Test
    void nobodyCanTypeIntoSomeoneElsesSpot() {
        editor.tryAcquire(editLock("session-A", 11L, "민준"));
        String key = TravelPlanEditorRealtimeService.editLockKey(ITEM_ID);

        assertThat(editor.updateDraft(PLAN_ID, key, "session-B", "몰래 수정")).isEmpty();
        assertThat(editor.find(PLAN_ID, key).orElseThrow().content()).isEmpty();
    }

    @Test
    void typingWithoutHoldingTheSpotDoesNothing() {
        assertThat(editor.updateDraft(PLAN_ID,
                TravelPlanEditorRealtimeService.editLockKey(ITEM_ID), "session-A", "경복궁"))
                .isEmpty();
        assertThat(editor.locksOf(PLAN_ID)).isEmpty();
    }

    // ── 연결이 끊길 때 ──────────────────────────────────────

    @Test
    void aClosedTabLetsGoOfEverythingItHeld() {
        editor.tryAcquire(editLock("session-A", 11L, "민준"));
        editor.updateDraft(PLAN_ID, TravelPlanEditorRealtimeService.editLockKey(ITEM_ID),
                "session-A", "작성 중");

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
                TravelPlanEditorRealtimeService.EDIT_MODE, DAY_ID, OTHER_ITEM_ID, 12L, "쭈니", "");
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
                TravelPlanEditorRealtimeService.EDIT_MODE, DAY_ID, ITEM_ID, 12L, "쭈니", "");

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
                TravelPlanEditorRealtimeService.EDIT_MODE, DAY_ID, OTHER_ITEM_ID, 11L, "민준", "");
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
                TravelPlanEditorRealtimeService.EDIT_MODE, DAY_ID, OTHER_ITEM_ID, 12L, "쭈니", "");
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
                "session-A", "경복궁");

        // 서버가 다시 뜨면 비어 있는 것이 정상이다
        assertThat(new TravelPlanEditorRealtimeService().locksOf(PLAN_ID)).isEmpty();
    }

    private EditorLock editLock(String sessionId, Long memberId, String displayName) {
        return new EditorLock(PLAN_ID, TravelPlanEditorRealtimeService.editLockKey(ITEM_ID),
                sessionId, TravelPlanEditorRealtimeService.EDIT_MODE,
                DAY_ID, ITEM_ID, memberId, displayName, "");
    }

    private EditorLock addLock(String sessionId, Long memberId, String displayName) {
        return new EditorLock(PLAN_ID, TravelPlanEditorRealtimeService.addLockKey(DAY_ID),
                sessionId, TravelPlanEditorRealtimeService.ADD_MODE,
                DAY_ID, null, memberId, displayName, "");
    }
}
