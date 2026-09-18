package com.example.travlediary.service.diary;

import com.example.travlediary.model.Diary;
import com.example.travlediary.repository.diary.DiaryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 4자리 PIN 잠금.
 *
 * <p>PIN 원문은 어디에도 남지 않는다 — DB 에는 해시만, 세션에는 푼 다이어리 번호만 둔다.
 * 소유권 확인은 늘 먼저이고, PIN 은 그 위에 한 겹 더 거는 잠금이다.
 */
@ExtendWith(MockitoExtension.class)
class DiaryPinServiceImplTest {

    @Mock
    private DiaryMapper diaryMapper;

    /** 회원 비밀번호와 같은 인코더를 그대로 쓴다. */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private DiaryPinSession diaryPinSession;
    private DiaryPinService service;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        diaryPinSession = new DiaryPinSession();
        service = new DiaryPinServiceImpl(diaryMapper, passwordEncoder, diaryPinSession);
        session = new MockHttpSession();
    }

    @Test
    void aFourDigitPinIsStoredOnlyAsAHash() {
        givenDiary(10L, 7L, null);
        when(diaryMapper.updatePinHash(eq(10L), eq(7L), any())).thenReturn(1);

        service.setPin(10L, 7L, "0427", session);

        ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
        verify(diaryMapper).updatePinHash(eq(10L), eq(7L), saved.capture());
        // 저장된 값은 원문이 아니고, 원문으로 맞춰 볼 수만 있다
        assertThat(saved.getValue()).isNotEqualTo("0427");
        assertThat(passwordEncoder.matches("0427", saved.getValue())).isTrue();
    }

    /**
     * 시나리오 A — 건 순간부터 실제로 잠긴다.
     * "PIN 을 걸었다"는 사실이 "열려 있다"는 뜻은 아니다. 방금 건 사람도 한 번 넣어야 한다.
     */
    @Test
    void settingAPinLocksTheDiaryRightAway() {
        givenDiary(10L, 7L, null);
        when(diaryMapper.updatePinHash(eq(10L), eq(7L), any())).thenReturn(1);
        // 걸기 전에 열려 있던 세션이어도 마찬가지다
        diaryPinSession.unlock(session, 10L);

        service.setPin(10L, 7L, "1234", session);

        assertThat(diaryPinSession.isUnlocked(session, 10L)).isFalse();
    }

    /** 시나리오 B — 실제로 PIN 을 넣어 맞혔을 때만 열린다. */
    @Test
    void onlyEnteringTheRightPinOpensTheDiary() {
        givenDiary(10L, 7L, passwordEncoder.encode("1234"));

        assertThat(service.isUnlocked(10L, 7L, session)).isFalse();
        assertThat(service.verifyAndUnlock(10L, 7L, "1234", session)).isTrue();
        assertThat(service.isUnlocked(10L, 7L, session)).isTrue();
    }

    /** 0 으로 시작하는 PIN 도 쓸 수 있다. (숫자가 아니라 글자로 다룬다) */
    @Test
    void aPinMayStartWithZero() {
        givenDiary(10L, 7L, null);
        when(diaryMapper.updatePinHash(eq(10L), eq(7L), any())).thenReturn(1);

        service.setPin(10L, 7L, "0000", session);

        ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
        verify(diaryMapper).updatePinHash(eq(10L), eq(7L), saved.capture());
        assertThat(passwordEncoder.matches("0000", saved.getValue())).isTrue();
    }

    @Test
    void anythingOtherThanFourDigitsIsRefused() {
        givenDiary(10L, 7L, null);

        for (String pin : List.of("123", "12345", "12a4", "12 4", " 1234", "")) {
            assertThatThrownBy(() -> service.setPin(10L, 7L, pin, session))
                    .as("%s", pin)
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("PIN은 숫자 4자리로 입력해 주세요.");
        }
        assertThatThrownBy(() -> service.setPin(10L, 7L, null, session))
                .isInstanceOf(ResponseStatusException.class);
        verify(diaryMapper, never()).updatePinHash(any(), any(), any());
    }

    /** 이미 걸린 잠금을 조용히 덮어쓰지 않는다. 바꾸려면 지금 PIN 을 확인해야 한다. */
    @Test
    void settingAPinTwiceIsRefused() {
        givenDiary(10L, 7L, passwordEncoder.encode("0427"));

        assertThatThrownBy(() -> service.setPin(10L, 7L, "1234", session))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PIN 잠금이 이미 설정되어 있습니다.");
        verify(diaryMapper, never()).updatePinHash(any(), any(), any());
    }

    /** 남의 다이어리는 PIN 을 걸 수도, 풀 수도 없다. (소유권이 늘 먼저다) */
    @Test
    void someoneElsesDiaryCannotBeTouched() {
        when(diaryMapper.findByIdAndUserId(10L, 8L)).thenReturn(null);

        assertThatThrownBy(() -> service.setPin(10L, 8L, "0427", session))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("다이어리를 찾을 수 없습니다.");
        assertThatThrownBy(() -> service.verifyAndUnlock(10L, 8L, "0427", session))
                .isInstanceOf(ResponseStatusException.class);
        verify(diaryMapper, never()).updatePinHash(any(), any(), any());
    }

    @Test
    void changingNeedsTheCurrentPinAndTheOldOneStopsWorking() {
        givenDiary(10L, 7L, passwordEncoder.encode("0427"));

        // 지금 PIN 이 틀리면 바뀌지 않는다
        assertThatThrownBy(() -> service.changePin(10L, 7L, "9999", "1234", session))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PIN 번호가 올바르지 않습니다.");
        verify(diaryMapper, never()).updatePinHash(any(), any(), any());

        when(diaryMapper.updatePinHash(eq(10L), eq(7L), any())).thenReturn(1);
        service.changePin(10L, 7L, "0427", "1234", session);

        ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
        verify(diaryMapper).updatePinHash(eq(10L), eq(7L), saved.capture());
        assertThat(passwordEncoder.matches("1234", saved.getValue())).isTrue();
        assertThat(passwordEncoder.matches("0427", saved.getValue())).isFalse();
        // 새 PIN 을 걸었으면 다시 잠근다. 다음에 들어갈 때 새 PIN 으로 한 번 열어야 한다
        assertThat(diaryPinSession.isUnlocked(session, 10L)).isFalse();
    }

    @Test
    void removingNeedsTheCurrentPinAndThenClearsTheHash() {
        givenDiary(10L, 7L, passwordEncoder.encode("0427"));

        assertThatThrownBy(() -> service.removePin(10L, 7L, "9999", session))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PIN 번호가 올바르지 않습니다.");
        verify(diaryMapper, never()).updatePinHash(any(), any(), any());

        when(diaryMapper.updatePinHash(10L, 7L, null)).thenReturn(1);
        service.removePin(10L, 7L, "0427", session);

        // 해시를 지운다. 다음부터는 묻지 않는다
        verify(diaryMapper).updatePinHash(10L, 7L, null);
    }

    /** 잠금이 걸려 있지 않으면 바꾸거나 풀 것도 없다. */
    @Test
    void changingOrRemovingWithoutALockIsRefused() {
        givenDiary(10L, 7L, null);

        assertThatThrownBy(() -> service.changePin(10L, 7L, "0427", "1234", session))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PIN 잠금이 설정되어 있지 않습니다.");
        assertThatThrownBy(() -> service.removePin(10L, 7L, "0427", session))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PIN 잠금이 설정되어 있지 않습니다.");
    }

    /** 잠금은 다이어리 한 권 단위다. A 를 풀었다고 B 까지 풀리지 않는다. */
    @Test
    void unlockingOneDiaryDoesNotUnlockAnother() {
        givenDiary(10L, 7L, passwordEncoder.encode("0427"));
        givenDiary(11L, 7L, passwordEncoder.encode("0427"));

        assertThat(service.verifyAndUnlock(10L, 7L, "0427", session)).isTrue();

        assertThat(service.isUnlocked(10L, 7L, session)).isTrue();
        assertThat(service.isUnlocked(11L, 7L, session)).isFalse();
    }

    /** 잠금이 없는 다이어리는 물어볼 것도 없이 열려 있다. */
    @Test
    void aDiaryWithoutAPinIsAlwaysOpen() {
        givenDiary(10L, 7L, null);

        assertThat(service.isUnlocked(10L, 7L, session)).isTrue();
        assertThat(service.isPinEnabled(10L, 7L)).isFalse();
    }

    /** 틀린 PIN 은 열지 못하고, 세션에는 아무 흔적도 남기지 않는다. */
    @Test
    void aWrongPinNeitherOpensNorLeavesTheRawValueBehind() {
        givenDiary(10L, 7L, passwordEncoder.encode("0427"));

        assertThat(service.verifyAndUnlock(10L, 7L, "9999", session)).isFalse();

        // 열리지 않는다 — 푼 다이어리 목록에 올라가지도 않는다
        assertThat(service.isUnlocked(10L, 7L, session)).isFalse();
        assertThat(unlockedIdsInSession()).doesNotContain(10L);

        // 남는 것은 그 다이어리의 실패 기록 하나뿐이고, 그 안에는 숫자만 있다
        Object attemptsRecord = attemptsInSession().get(10L);
        assertThat(attemptsRecord).isNotNull();
        assertThat(instanceFieldTypesOf(attemptsRecord))
                .isNotEmpty()
                .allMatch(Class::isPrimitive);

        // 세션 값 그래프 어디에도 문자열 자체가 저장되어 있지 않다.
        // 원문("9999"/"0427")도, 맞춰 볼 수 있는 해시("$2...")도 없다는 뜻이다.
        assertThat(stringsStoredInSession()).isEmpty();
    }

    /** 잇달아 틀리면 잠시 쉬어 간다. 맞는 PIN 이라도 그동안은 확인하지 않는다. */
    @Test
    void tooManyWrongTriesBlockTheDiaryForAWhile() {
        givenDiary(10L, 7L, passwordEncoder.encode("0427"));

        for (int i = 0; i < DiaryPinSession.MAX_ATTEMPTS; i++) {
            assertThat(service.verifyAndUnlock(10L, 7L, "9999", session)).isFalse();
        }

        assertThatThrownBy(() -> service.verifyAndUnlock(10L, 7L, "0427", session))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PIN 입력 횟수를 초과했습니다.");
        // 다른 다이어리의 실패 횟수와 섞이지 않는다
        givenDiary(11L, 7L, passwordEncoder.encode("0427"));
        assertThat(service.verifyAndUnlock(11L, 7L, "0427", session)).isTrue();
    }

    /** 맞히면 그 다이어리의 실패 기록은 지워진다. */
    @Test
    void aSuccessClearsTheFailureCount() {
        givenDiary(10L, 7L, passwordEncoder.encode("0427"));

        for (int i = 0; i < DiaryPinSession.MAX_ATTEMPTS - 1; i++) {
            service.verifyAndUnlock(10L, 7L, "9999", session);
        }
        assertThat(service.verifyAndUnlock(10L, 7L, "0427", session)).isTrue();

        // 기록이 지워졌으므로 다시 처음부터 셀 수 있다
        for (int i = 0; i < DiaryPinSession.MAX_ATTEMPTS - 1; i++) {
            assertThat(service.verifyAndUnlock(10L, 7L, "9999", session)).isFalse();
        }
        assertThat(service.verifyAndUnlock(10L, 7L, "0427", session)).isTrue();
    }

    private void givenDiary(Long diaryId, Long userId, String pinHash) {
        Diary diary = new Diary();
        diary.setId(diaryId);
        diary.setUserId(userId);
        diary.setPinHash(pinHash);
        lenient().when(diaryMapper.findByIdAndUserId(diaryId, userId)).thenReturn(diary);
    }

    /* ===== 세션에 실제로 무엇이 담겼는지 들여다보는 도우미 =====
     *
     * 객체의 toString() 을 문자열로 훑지 않는다. 기본 toString 에는 identity hash 가 섞여 있어
     * (예: ...Attempts@3b0427b9) 저장하지도 않은 PIN "0427" 이 들어 있는 것처럼 보일 수 있다.
     * 그래서 담긴 값을 필드 단위로 따라 내려가며 확인한다.
     */

    /** DiaryPinSession 이 쓰는 세션 키. 값이 아니라 저장 위치라서 테스트에서도 그대로 쓴다. */
    private static final String UNLOCKED_ATTRIBUTE = "diaryPinUnlockedIds";
    private static final String ATTEMPTS_ATTRIBUTE = "diaryPinAttempts";

    /** 이 세션에서 잠금이 풀린 다이어리 번호. 아직 하나도 없으면 빈 목록. */
    private List<Object> unlockedIdsInSession() {
        Object value = session.getAttribute(UNLOCKED_ATTRIBUTE);
        return value instanceof Collection<?> ids ? List.copyOf(ids) : List.of();
    }

    /** 다이어리 번호별 실패 기록. 아직 없으면 빈 맵. */
    private Map<?, ?> attemptsInSession() {
        Object value = session.getAttribute(ATTEMPTS_ATTRIBUTE);
        return value instanceof Map<?, ?> attempts ? attempts : Map.of();
    }

    /** 그 객체가 실제로 들고 있는 인스턴스 필드의 타입들. */
    private List<Class<?>> instanceFieldTypesOf(Object value) {
        return Arrays.stream(value.getClass().getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType)
                .toList();
    }

    /** 세션 값 그래프 안에 실제로 저장된 문자열 전부. */
    private List<String> stringsStoredInSession() {
        List<String> found = new ArrayList<>();
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (String name : Collections.list(session.getAttributeNames())) {
            collectStrings(session.getAttribute(name), found, visited);
        }
        return found;
    }

    private void collectStrings(Object value, List<String> found, Set<Object> visited) {
        if (value == null || value instanceof Number || value instanceof Boolean
                || value instanceof Character || !visited.add(value)) {
            return;
        }
        if (value instanceof CharSequence text) {
            found.add(text.toString());
        } else if (value instanceof Map<?, ?> map) {
            map.forEach((key, mapped) -> {
                collectStrings(key, found, visited);
                collectStrings(mapped, found, visited);
            });
        } else if (value instanceof Iterable<?> items) {
            items.forEach(item -> collectStrings(item, found, visited));
        } else {
            for (Field field : value.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    collectStrings(field.get(value), found, visited);
                } catch (IllegalAccessException exception) {
                    throw new AssertionError("세션에 담긴 값을 들여다볼 수 없습니다: " + field, exception);
                }
            }
        }
    }
}
