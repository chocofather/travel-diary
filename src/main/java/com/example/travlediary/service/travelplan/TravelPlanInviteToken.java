package com.example.travlediary.service.travelplan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 초대 링크의 raw token 생성과 저장용 해시.
 * 해시 방식은 비밀번호 재설정 토큰(ResetTokenHasher)과 같은 관례를 따른다.
 *
 * <p>raw token 은 발급 응답에서 한 번만 보여 주고 저장하지 않는다.
 * DB 에는 SHA-256 hex 만 남으므로 DB 를 읽어도 링크를 복원할 수 없다.
 */
public final class TravelPlanInviteToken {

    private static final String ALGORITHM = "SHA-256";
    /** 32바이트 = 256bit. 요구 최소치(128bit)를 넉넉히 넘긴다. */
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** URL 에 그대로 들어가도록 padding 없는 URL-safe Base64 를 쓴다. */
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private TravelPlanInviteToken() {
    }

    /** 예측 불가능한 새 raw token. 호출할 때마다 다른 값이 나온다. */
    public static String newRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /** @return travel_plan_invitations.token_hash 에 넣을 hex 64자 */
    public static String hash(String rawToken) {
        if (rawToken == null) {
            throw new IllegalArgumentException("초대 토큰이 필요합니다.");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("초대 토큰을 안전하게 처리할 수 없습니다.", exception);
        }
    }
}
