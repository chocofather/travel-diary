package com.example.travlediary.dto;

import java.util.List;

/**
 * 지금 여행 계획을 완료할 수 있는지.
 *
 * <p>완료하기 전에 한 번 물어보는 용도다. 이 응답만으로 완료되지는 않는다.
 * 물어본 뒤 완료하기 전 사이에 누가 편집을 시작할 수 있어서,
 * 실제로 완료할 때 서버가 같은 조건을 다시 본다.
 *
 * <p>누가 일정을 쓰고 있다고 해서 완료를 막지는 않는다.
 * 막는 대신 누가 쓰고 있는지 알려 주고, 그래도 할지는 방장이 정한다.
 *
 * @param canFinalize               방장이고 살아 있는 방인지. 자격 문제는 여기까지 오지 않는다
 * @param activeEditorExists        지금 일정을 쓰고 있는 다른 사람이 있는지
 * @param activeEditorDisplayNames  그 사람들의 방 안 표시 이름. 없으면 빈 목록
 */
public record TravelPlanFinalizeCheckDto(
        boolean canFinalize,
        boolean activeEditorExists,
        List<String> activeEditorDisplayNames) {

    /** 아무도 쓰고 있지 않다. 그대로 진행하면 된다. */
    public static TravelPlanFinalizeCheckDto ready() {
        return new TravelPlanFinalizeCheckDto(true, false, List.of());
    }

    /**
     * 쓰고 있는 사람이 있다. 완료할 수는 있지만 먼저 알려 준다.
     * 문장으로 만드는 것은 화면이 한다. 여기서는 누구인지까지다.
     */
    public static TravelPlanFinalizeCheckDto warnAbout(List<String> displayNames) {
        return new TravelPlanFinalizeCheckDto(true, true, List.copyOf(displayNames));
    }
}
