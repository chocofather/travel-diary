package com.example.travlediary.service.diary;

import jakarta.servlet.http.HttpSession;

/**
 * 개인 여행일기에 거는 4자리 PIN 잠금.
 *
 * <p>PIN 은 로그인이나 소유권을 대신하지 않는다. 본인 다이어리에 한 겹 더 거는 잠금이라,
 * 어떤 작업이든 <b>소유권 확인이 먼저</b>이고 PIN 확인은 그다음이다.
 *
 * <p>PIN 원문은 어디에도 남기지 않는다. 저장하는 것은 해시뿐이고,
 * 세션에는 잠금을 푼 다이어리 번호만 둔다. 저장된 PIN 을 되돌려 읽는 길은 만들지 않는다.
 */
public interface DiaryPinService {

    /** 그 다이어리에 PIN 잠금이 걸려 있는지. (해시의 유무로만 판단한다) */
    boolean isPinEnabled(Long diaryId, Long userId);

    /**
     * PIN 을 처음 건다. 이미 걸려 있으면 조용히 덮어쓰지 않고 막는다. (바꾸려면 변경 쪽을 쓴다)
     *
     * <p>건 순간부터 실제로 잠긴다. 걸었다는 사실은 열어 두었다는 뜻이 아니므로,
     * 방금 건 사람도 들어갈 때 PIN 을 한 번 넣어야 한다.
     */
    void setPin(Long diaryId, Long userId, String newPin, HttpSession session);

    /**
     * PIN 을 바꾼다. 지금 PIN 이 맞아야 한다.
     * 바꾼 뒤에는 예전 PIN 으로 열 수 없고, 새 PIN 으로 다시 한 번 열어야 한다.
     */
    void changePin(Long diaryId, Long userId, String currentPin, String newPin,
                   HttpSession session);

    /**
     * PIN 잠금을 아주 없앤다. 지금 PIN 이 맞아야 한다.
     * (한 번 여는 것과 다르다 — 해시 자체를 지우므로 다음부터 묻지 않는다)
     */
    void removePin(Long diaryId, Long userId, String currentPin, HttpSession session);

    /**
     * PIN 을 확인하고 맞으면 이 세션에서 그 다이어리만 연다.
     *
     * <p>잇달아 틀리면 잠시 쉬어 간다. 맞히면 그 다이어리의 실패 기록은 지워진다.
     *
     * @return 맞았는지 여부. (틀린 이유를 자세히 알려 주지 않는다)
     */
    boolean verifyAndUnlock(Long diaryId, Long userId, String pin, HttpSession session);

    /** 이 세션에서 그 다이어리를 열어 둔 상태인지. (잠금이 없으면 늘 true) */
    boolean isUnlocked(Long diaryId, Long userId, HttpSession session);
}
