package com.example.travlediary.service.diary;

import com.example.travlediary.model.Diary;
import com.example.travlediary.repository.diary.DiaryMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DiaryPinServiceImpl implements DiaryPinService {

    /** PIN 은 숫자 네 자리 고정이다. 0 으로 시작할 수 있어 숫자가 아니라 글자로 다룬다. */
    private static final Pattern PIN = Pattern.compile("^[0-9]{4}$");

    static final String INVALID_FORMAT = "PIN은 숫자 4자리로 입력해 주세요.";
    static final String WRONG_PIN = "PIN 번호가 올바르지 않습니다.";
    static final String ALREADY_SET = "PIN 잠금이 이미 설정되어 있습니다.";
    static final String NOT_SET = "PIN 잠금이 설정되어 있지 않습니다.";
    static final String TOO_MANY_ATTEMPTS = "PIN 입력 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요.";

    private final DiaryMapper diaryMapper;
    /** 회원 비밀번호와 같은 인코더를 그대로 쓴다. */
    private final PasswordEncoder passwordEncoder;
    private final DiaryPinSession diaryPinSession;

    @Override
    @Transactional(readOnly = true)
    public boolean isPinEnabled(Long diaryId, Long userId) {
        return requireOwnedDiary(diaryId, userId).isPinEnabled();
    }

    @Override
    @Transactional
    public void setPin(Long diaryId, Long userId, String newPin, HttpSession session) {
        Diary diary = requireOwnedDiary(diaryId, userId);
        // 이미 걸려 있는 잠금을 조용히 덮어쓰지 않는다. 바꾸려면 지금 PIN 을 확인해야 한다.
        if (diary.isPinEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ALREADY_SET);
        }
        savePinHash(diaryId, userId, encode(newPin));
        /*
          건 순간부터 실제로 잠긴다. "걸었다"는 사실이 "열려 있다"는 뜻은 아니다.
          방금 건 사람도 들어갈 때는 PIN 을 한 번 넣어야 한다 —
          그래야 잠금이 제대로 걸렸는지 스스로 확인하게 된다.
        */
        diaryPinSession.lock(session, diaryId);
        diaryPinSession.resetFailures(session, diaryId);
    }

    @Override
    @Transactional
    public void changePin(Long diaryId, Long userId, String currentPin, String newPin,
                          HttpSession session) {
        Diary diary = requireOwnedDiary(diaryId, userId);
        requirePinEnabled(diary);
        requireCurrentPin(diary, currentPin);
        // 새 PIN 의 형식은 지금 PIN 을 확인한 뒤에 본다.
        savePinHash(diaryId, userId, encode(newPin));
        // 새 PIN 을 걸었으면 다시 잠근다. 다음에 들어갈 때 새 PIN 으로 한 번 열어야 한다.
        diaryPinSession.lock(session, diaryId);
        diaryPinSession.resetFailures(session, diaryId);
    }

    @Override
    @Transactional
    public void removePin(Long diaryId, Long userId, String currentPin, HttpSession session) {
        Diary diary = requireOwnedDiary(diaryId, userId);
        requirePinEnabled(diary);
        requireCurrentPin(diary, currentPin);
        // 해시 자체를 지운다. 다음부터는 묻지 않는다.
        savePinHash(diaryId, userId, null);
        diaryPinSession.lock(session, diaryId);
        diaryPinSession.resetFailures(session, diaryId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifyAndUnlock(Long diaryId, Long userId, String pin, HttpSession session) {
        Diary diary = requireOwnedDiary(diaryId, userId);
        requirePinEnabled(diary);
        // 잇달아 틀린 뒤에는 잠시 쉬어 간다. (맞는 PIN 이라도 이때는 확인하지 않는다)
        if (diaryPinSession.isBlocked(session, diaryId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_ATTEMPTS);
        }

        /*
          형식이 어긋난 값도 틀린 것으로 센다.
          맞는 형식만 세면 몇 번이든 두드려 볼 수 있는 길이 남는다.
        */
        if (!matches(pin, diary.getPinHash())) {
            diaryPinSession.recordFailure(session, diaryId);
            return false;
        }
        diaryPinSession.unlock(session, diaryId);
        diaryPinSession.resetFailures(session, diaryId);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUnlocked(Long diaryId, Long userId, HttpSession session) {
        Diary diary = requireOwnedDiary(diaryId, userId);
        // 잠금이 없는 다이어리는 물어볼 것도 없다.
        return !diary.isPinEnabled() || diaryPinSession.isUnlocked(session, diaryId);
    }

    /**
     * 소유권 확인. 남의 다이어리와 없는 다이어리는 기존 정책 그대로 404 다.
     *
     * <p>여기서는 소유권만 보고 잠금 검사는 하지 않는다. 잠긴 다이어리라도
     * 풀거나 바꾸려면 읽을 수 있어야 하기 때문이다.
     * (일반 조회 길인 DiaryService.getMyDiary 는 잠금까지 함께 본다)
     */
    private Diary requireOwnedDiary(Long diaryId, Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        Diary diary = diaryId == null ? null : diaryMapper.findByIdAndUserId(diaryId, userId);
        if (diary == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "다이어리를 찾을 수 없습니다.");
        }
        return diary;
    }

    private void requirePinEnabled(Diary diary) {
        if (!diary.isPinEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, NOT_SET);
        }
    }

    /** 지금 PIN 이 맞는지. 틀린 이유(형식/불일치)를 나눠 알려 주지 않는다. */
    private void requireCurrentPin(Diary diary, String currentPin) {
        if (!matches(currentPin, diary.getPinHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, WRONG_PIN);
        }
    }

    private boolean matches(String pin, String pinHash) {
        return pin != null && PIN.matcher(pin).matches()
                && pinHash != null && passwordEncoder.matches(pin, pinHash);
    }

    /** 저장할 해시. 형식이 어긋난 PIN 은 여기서 막는다. (원문은 어디에도 남기지 않는다) */
    private String encode(String pin) {
        if (pin == null || !PIN.matcher(pin).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_FORMAT);
        }
        return passwordEncoder.encode(pin);
    }

    private void savePinHash(Long diaryId, Long userId, String pinHash) {
        if (diaryMapper.updatePinHash(diaryId, userId, pinHash) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "다이어리를 찾을 수 없습니다.");
        }
    }
}
