package com.example.travlediary.service.travelplan;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 초대 링크 재표시용 암복호화.
 * 여기 쓰는 키는 이 테스트에서만 만들어 쓰는 값이고 운영 키와 무관하다.
 */
class TravelPlanInviteTokenCipherTest {

    /** 32바이트(AES-256) 테스트 키. 매 실행마다 새로 만든다. */
    private static String newTestKey() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private TravelPlanInviteTokenCipher cipher(String base64Key) {
        return new TravelPlanInviteTokenCipher(base64Key);
    }

    @Test
    void aTokenSurvivesTheRoundTrip() {
        TravelPlanInviteTokenCipher cipher = cipher(newTestKey());
        String rawToken = TravelPlanInviteToken.newRawToken();

        String stored = cipher.encrypt(rawToken);

        assertThat(cipher.decrypt(stored)).contains(rawToken);
    }

    @Test
    void whatGoesIntoTheDatabaseIsNotTheTokenItself() {
        TravelPlanInviteTokenCipher cipher = cipher(newTestKey());
        String rawToken = TravelPlanInviteToken.newRawToken();

        String stored = cipher.encrypt(rawToken);

        assertThat(stored)
                .isNotEqualTo(rawToken)
                .doesNotContain(rawToken)
                // URL-safe Base64 한 덩어리로 저장한다
                .matches("[A-Za-z0-9_-]+");
        // IV 12 + 평문 43 + tag 16 = 71 바이트. 컬럼 512 자 안에 넉넉히 들어간다
        assertThat(Base64.getUrlDecoder().decode(stored)).hasSize(71);
        assertThat(stored.length()).isLessThan(512);
    }

    @Test
    void theSameTokenNeverEncryptsToTheSameValueTwice() {
        TravelPlanInviteTokenCipher cipher = cipher(newTestKey());
        String rawToken = TravelPlanInviteToken.newRawToken();

        // 매번 새 IV 를 쓰므로 같은 평문이라도 저장값이 달라진다
        String first = cipher.encrypt(rawToken);
        String second = cipher.encrypt(rawToken);

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).contains(rawToken);
        assertThat(cipher.decrypt(second)).contains(rawToken);
    }

    @Test
    void aTamperedValueDoesNotDecrypt() {
        TravelPlanInviteTokenCipher cipher = cipher(newTestKey());
        String stored = cipher.encrypt(TravelPlanInviteToken.newRawToken());

        // 암호문 한가운데 한 글자만 바꿔도 GCM 인증에서 걸린다.
        // (마지막 글자는 남는 비트를 담고 있어 바꿔도 같은 바이트로 디코딩될 수 있다)
        int middle = stored.length() / 2;
        char original = stored.charAt(middle);
        String tampered = stored.substring(0, middle)
                + (original == 'A' ? 'B' : 'A')
                + stored.substring(middle + 1);

        // 정말로 다른 바이트가 되었는지 먼저 확인한다
        assertThat(Base64.getUrlDecoder().decode(tampered))
                .isNotEqualTo(Base64.getUrlDecoder().decode(stored));
        assertThat(cipher.decrypt(tampered)).isEmpty();
    }

    @Test
    void anotherKeyCannotOpenIt() {
        String stored = cipher(newTestKey()).encrypt(TravelPlanInviteToken.newRawToken());

        assertThat(cipher(newTestKey()).decrypt(stored)).isEmpty();
    }

    @Test
    void garbageInputIsJustAnEmptyResult() {
        TravelPlanInviteTokenCipher cipher = cipher(newTestKey());

        // 화면이 죽지 않고 재발급 안내로 넘어가야 하므로 예외를 던지지 않는다
        for (String broken : new String[]{null, "", "   ", "not-base64!!", "AAAA"}) {
            assertThat(cipher.decrypt(broken)).as("input=%s", broken).isEmpty();
        }
    }

    @Test
    void withoutAKeyNothingIsStoredAndTheErrorNamesTheSetting() {
        // 키가 없어도 앱은 뜬다. 초대 발급에서만 분명하게 막힌다
        TravelPlanInviteTokenCipher cipher = cipher("");

        assertThat(cipher.isConfigured()).isFalse();
        assertThat(cipher.decrypt("anything")).isEmpty();
        assertThatThrownBy(() -> cipher.encrypt("raw-token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TRAVEL_PLAN_INVITE_ENCRYPTION_KEY")
                // 메시지에 토큰이 실려 나가지 않는다
                .hasMessageNotContaining("raw-token");
    }

    @Test
    void aKeyOfTheWrongShapeIsRejectedWithoutEchoingIt() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> cipher(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트")
                .hasMessageNotContaining(shortKey);
        assertThatThrownBy(() -> cipher("not base64!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64")
                .hasMessageNotContaining("not base64!!");
    }

    @Test
    void encryptingNothingIsARefusalNotASilentStore() {
        TravelPlanInviteTokenCipher cipher = cipher(newTestKey());

        assertThatThrownBy(() -> cipher.encrypt(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theHashPolicyIsUntouchedByEncryption() {
        TravelPlanInviteTokenCipher cipher = cipher(newTestKey());
        String rawToken = TravelPlanInviteToken.newRawToken();

        // 검증은 계속 SHA-256 해시로만 한다. 암호문과는 별개다
        assertThat(TravelPlanInviteToken.hash(rawToken))
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isNotEqualTo(cipher.encrypt(rawToken));
    }
}
