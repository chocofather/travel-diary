package com.example.travlediary.dto;

/**
 * 지금 누가 어디를 붙잡고 무엇을 쓰고 있는지.
 * WebSocket 으로 나가는 값이라 방 안에서만 뜻이 있는 값만 담는다
 * (계정 정보는 담지 않고, 이름은 방 전용 display_name 이다).
 *
 * @param lockKey        "ADD:{dayId}" / "ITEM:{itemId}"
 *                       / "ALT_ADD:{itemId}" / "ALT:{alternativeId}"
 * @param mode           ADD / EDIT / ALT_ADD / ALT_EDIT
 * @param itemId         새 일정 자리면 null
 * @param alternativeId  대안 수정일 때만 값이 있다
 * @param conditionLabel 대안의 조건. A 일정에서는 null
 * @param content        아직 저장되지 않은 작성 중 내용
 * @param memberId       작성자의 travel_plan_members.id
 * @param displayName    방 전용 표시 이름
 */
public record TravelPlanEditorLockDto(
        String lockKey,
        String mode,
        Long dayId,
        Long itemId,
        Long alternativeId,
        String conditionLabel,
        String content,
        Long memberId,
        String displayName) {
}
