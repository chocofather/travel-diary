package com.example.travlediary.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * provider 가 인증한 이메일이 기존 회원의 이메일과 같을 때, 연결 확인 화면이 끝날 때까지만
 * 서버 세션에 두는 짧은 수명의 문맥.
 *
 * <p>대상 회원 id 는 요청 파라미터로 오가지 않고 오직 여기에만 있다. 확인 POST 는 세션의 이 값과
 * 화면이 내려준 flowId 를 함께 확인하므로, URL 을 고쳐 다른 계정에 연결할 수 없다.
 */
public record PendingSocialLink(
        String flowId,
        SocialProvider provider,
        String providerUserId,
        String email,
        Long targetUserId,
        Instant createdAt,
        Instant expiresAt
) implements Serializable {

    public static final String SESSION_ATTRIBUTE = "pendingSocialLink";

    public boolean isExpired(Instant now) {
        return expiresAt == null || !now.isBefore(expiresAt);
    }

    /** 확인 POST 가 이 화면에서 시작된 것인지 본다. flowId 는 hidden 필드로 다시 올라온다. */
    public boolean matchesFlow(String submittedFlowId) {
        return flowId != null && !flowId.isBlank() && flowId.equals(submittedFlowId);
    }
}
