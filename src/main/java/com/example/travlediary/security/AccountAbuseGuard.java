package com.example.travlediary.security;

/**
 * 회원 관련 공개 endpoint 의 요청 남용을 막는 경계.
 *
 * <p>Controller 와 Service 는 "얼마나 허용되는가" 를 알지 않고 이 문만 지난다. 나중에 서버를
 * 여러 대로 늘려 공유 저장소(Redis 등) 기반으로 바꿀 때 구현만 갈아 끼우면 된다.
 *
 * <p>막는 축은 둘이다.
 * <ul>
 *   <li><b>IP</b> — 자동화된 대량 조회와 메일 발송을 막는다. 주소를 바꿔 가며 부르는 공격도
 *       같은 IP 안에서는 함께 걸린다.</li>
 *   <li><b>이메일</b> — 같은 주소로 메일이 거듭 날아가지 않게 한다. 기존 인증 메일 재전송
 *       cooldown 과 같은 길이를 쓴다.</li>
 * </ul>
 */
public interface AccountAbuseGuard {

    /**
     * 회원 존재 확인 조회(아이디/이메일/닉네임 중복 확인) 한 번.
     *
     * @throws TooManyAccountRequestsException 같은 IP 에서 너무 많이 조회했을 때
     */
    void checkExistenceLookup(String ipAddress);

    /**
     * 계정 복구·인증 메일 요청 한 번. 실제로 메일이 나가는지와 무관하게 먼저 센다.
     *
     * <p>회원이 있는지 확인하기 전에 세어야 존재 여부가 응답 차이로 드러나지 않는다.
     *
     * @throws TooManyAccountRequestsException 같은 IP 에서 너무 많이 요청했을 때
     */
    void checkRecoveryRequest(String ipAddress);

    /**
     * 같은 주소로 메일을 다시 보내도 되는지.
     *
     * <p>막혀도 예외를 던지지 않는다 — 부르는 쪽은 메일만 거르고 응답은 그대로 두어야
     * 계정이 있는지 없는지가 드러나지 않기 때문이다.
     *
     * @param normalizedEmail {@code EmailPolicy.normalizeAndValidate} 를 지난 주소
     * @return 보내도 되면 true, 아직 쉬어 갈 때면 false
     */
    boolean allowRecoveryEmail(RecoveryEmailKind kind, String normalizedEmail);

    /** 메일 종류. 종류가 다르면 서로의 cooldown 에 걸리지 않는다. */
    enum RecoveryEmailKind {
        /** 아이디 안내 메일 */
        USERNAME_RECOVERY,
        /** 비밀번호 재설정 링크 메일 */
        PASSWORD_RESET
    }
}
