package com.example.travlediary.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * 잘못 입력한 이메일을 고치기 위한 본인확인 문맥.
 *
 * <p>확인 방법만 가입 경로에 따라 다르다. 비밀번호가 있는 계정은 그 비밀번호로, 소셜 계정은 그
 * 계정에 실제로 연결된 provider 재인증으로 확인한다. {@code authorized} 는 그 확인이 끝났다는
 * 뜻이고, true 가 되기 전에는 어떤 화면도 이메일을 바꿀 수 없다. 확인에 성공해도 로그인은 아니다.
 *
 * <p>대상 회원 id 와 지금 인증 대기 중인 이메일은 오직 여기에만 있다. 요청 파라미터나 hidden 필드로
 * 오가지 않으므로 URL 을 고쳐 다른 계정의 이메일을 바꿀 수 없다.
 */
public record PendingEmailCorrection(
        String flowId,
        Long targetUserId,
        String expectedCurrentEmail,
        Method method,
        SocialProvider provider,
        boolean authorized,
        Instant createdAt,
        Instant expiresAt
) implements Serializable {

    public static final String SESSION_ATTRIBUTE = "pendingEmailCorrection";

    /** 본인확인 방법. */
    public enum Method {
        /** 계정 비밀번호를 다시 확인한다. */
        LOCAL,
        /** 계정에 연결된 Kakao/Naver 로 다시 인증한다. */
        SOCIAL
    }

    public boolean isExpired(Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    public boolean isValidAt(Instant now) {
        if (flowId == null || flowId.isBlank()
                || targetUserId == null
                || expectedCurrentEmail == null || expectedCurrentEmail.isBlank()
                || method == null
                || createdAt == null
                || isExpired(now)) {
            return false;
        }
        return method == Method.LOCAL
                ? provider == null
                : provider == SocialProvider.KAKAO || provider == SocialProvider.NAVER;
    }

    /** 본인확인까지 끝나 이메일을 바꿀 수 있는 상태인지. */
    public boolean isAuthorizedAt(Instant now) {
        return authorized && isValidAt(now);
    }

    /** 본인확인 성공. 만료 시각은 늘리지 않는다. */
    public PendingEmailCorrection authorize() {
        return new PendingEmailCorrection(flowId, targetUserId, expectedCurrentEmail,
                method, provider, true, createdAt, expiresAt);
    }
}
