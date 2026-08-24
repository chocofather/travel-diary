package com.example.travlediary.service.travelplan;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 초대 토큰 생성과 저장용 해시.
 * raw token 은 발급 응답에서만 살아 있고, DB 에는 SHA-256 hex 만 남는다.
 */
class TravelPlanInviteTokenTest {

    @Test
    void aRawTokenCarriesFarMoreThanTheRequiredEntropy() {
        String rawToken = TravelPlanInviteToken.newRawToken();

        // padding 없는 URL-safe Base64 32바이트 = 43자
        assertThat(rawToken).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(Base64.getUrlDecoder().decode(rawToken)).hasSize(32);
    }

    @Test
    void everyCallProducesADifferentToken() {
        Set<String> tokens = new HashSet<>();
        for (int index = 0; index < 500; index++) {
            tokens.add(TravelPlanInviteToken.newRawToken());
        }
        assertThat(tokens).hasSize(500);
    }

    @Test
    void theStoredValueIsTheSha256HexOfTheRawToken() throws Exception {
        String rawToken = TravelPlanInviteToken.newRawToken();

        String hash = TravelPlanInviteToken.hash(rawToken);

        // token_hash 는 CHAR(64) 이므로 hex 64자여야 한다
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        byte[] expected = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));
        assertThat(hash).isEqualTo(java.util.HexFormat.of().formatHex(expected));
        // 해시에서 raw token 을 되찾을 수 없다
        assertThat(hash).doesNotContain(rawToken);
    }

    @Test
    void hashingIsStableAndDiffersPerToken() {
        String first = TravelPlanInviteToken.newRawToken();
        String second = TravelPlanInviteToken.newRawToken();

        assertThat(TravelPlanInviteToken.hash(first))
                .isEqualTo(TravelPlanInviteToken.hash(first))
                .isNotEqualTo(TravelPlanInviteToken.hash(second));
    }

    @Test
    void aMissingTokenIsRejectedInsteadOfHashingNull() {
        assertThatThrownBy(() -> TravelPlanInviteToken.hash(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anyStringCanBeHashedSoAMalformedTokenNeverBlowsUp() {
        // 링크를 손으로 고쳐 넣어도 조회 단계까지 조용히 내려가야 한다
        for (String malformed : new String[]{"", "   ", "not-a-token", "../../etc/passwd", "한글"}) {
            assertThat(TravelPlanInviteToken.hash(malformed))
                    .as("malformed=%s", malformed)
                    .matches("[0-9a-f]{64}");
        }
    }
}
