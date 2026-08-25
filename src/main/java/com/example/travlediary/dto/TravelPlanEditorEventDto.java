package com.example.travlediary.dto;

import java.util.List;

/**
 * 작성 중 상태가 바뀌었다는 알림.
 *
 * <p>{@code LOCKED} / {@code DRAFT} 는 그 자리 하나의 상태를,
 * {@code UNLOCKED} 는 놓인 자리를, {@code SNAPSHOT} 은 방 전체의 현재 상태를 담는다.
 * (SNAPSHOT 은 끊겼다 다시 붙었을 때 화면을 맞추는 데 쓴다)
 *
 * @param requestId 잠금을 요청한 사람에게만 돌아가는 응답에서 어떤 요청의 답인지 알려 준다
 * @param granted   잠금 요청의 성공 여부. 그 밖의 알림에서는 null
 */
public record TravelPlanEditorEventDto(
        String type,
        TravelPlanEditorLockDto lock,
        List<TravelPlanEditorLockDto> locks,
        String requestId,
        Boolean granted) {

    public static TravelPlanEditorEventDto locked(TravelPlanEditorLockDto lock) {
        return new TravelPlanEditorEventDto("LOCKED", lock, null, null, null);
    }

    public static TravelPlanEditorEventDto draft(TravelPlanEditorLockDto lock) {
        return new TravelPlanEditorEventDto("DRAFT", lock, null, null, null);
    }

    public static TravelPlanEditorEventDto unlocked(TravelPlanEditorLockDto lock) {
        return new TravelPlanEditorEventDto("UNLOCKED", lock, null, null, null);
    }

    public static TravelPlanEditorEventDto snapshot(List<TravelPlanEditorLockDto> locks) {
        return new TravelPlanEditorEventDto("SNAPSHOT", null, locks, null, null);
    }

    /** 잠금을 요청한 사람에게만 돌려주는 결과. */
    public static TravelPlanEditorEventDto lockResult(
            String requestId, boolean granted, TravelPlanEditorLockDto lock) {
        return new TravelPlanEditorEventDto("LOCK_RESULT", lock, null, requestId, granted);
    }
}
