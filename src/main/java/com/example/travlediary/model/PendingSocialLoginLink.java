package com.example.travlediary.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * Kakao/Naver 가입 중에 이미 가입된 이메일이 나와, 기존 계정으로 로그인해 소유권을 확인한 뒤
 * 그 계정에 지금의 provider 로그인을 붙이기로 한 상태.
 *
 * <p>DB 토큰을 쓰지 않는다. 기존 계정 로그인 자체가 소유권 증명이고, provider 소유권은 방금 끝난
 * OAuth 가 증명한다. 마이페이지 소셜 연결과 같은 원리라 이메일을 한 번 더 보내지 않는다.
 *
 * <p>연결 대상 회원 id 와 provider 식별자는 오직 여기에만 있다. 요청 파라미터나 hidden 필드로
 * 오가지 않으므로 URL 을 고쳐 다른 계정에 붙일 수 없다.
 */
public record PendingSocialLoginLink(
        String flowId,
        SocialProvider provider,
        String providerUserId,
        String providerEmail,
        Boolean providerEmailVerified,
        Long targetUserId,
        String normalizedTargetEmail,
        Instant createdAt,
        Instant expiresAt
) implements Serializable {

    public static final String SESSION_ATTRIBUTE = "pendingSocialLoginLink";

    public boolean isExpired(Instant now) {
        return expiresAt == null || !now.isBefore(expiresAt);
    }

    /** 연결에 쓸 수 있는 문맥인지. 만료·손상된 값은 여기서 걸러진다. */
    public boolean isUsableAt(Instant now) {
        return flowId != null && !flowId.isBlank()
                && (provider == SocialProvider.KAKAO || provider == SocialProvider.NAVER)
                && providerUserId != null && !providerUserId.isBlank()
                && targetUserId != null
                && normalizedTargetEmail != null && !normalizedTargetEmail.isBlank()
                && createdAt != null
                && !isExpired(now);
    }
}
