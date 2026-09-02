package com.example.travlediary.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class PendingSocialWithdrawalTest {

    @Test
    void storesOnlyOneTimeIntentMetadataWithoutOAuthTokensOrAttributes() {
        assertThat(Arrays.stream(PendingSocialWithdrawal.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly("flowId", "userId", "provider", "createdAt", "expiresAt")
                .noneMatch(name -> name.toLowerCase().contains("token")
                        || name.toLowerCase().contains("attribute")
                        || name.toLowerCase().contains("secret"));
    }

    @Test
    void expirationIsStrictlyValidated() {
        Instant now = Instant.parse("2026-09-02T03:00:00Z");
        PendingSocialWithdrawal valid = new PendingSocialWithdrawal(
                "flow", 7L, SocialProvider.GOOGLE, now, now.plusSeconds(1));
        PendingSocialWithdrawal expired = new PendingSocialWithdrawal(
                "flow", 7L, SocialProvider.GOOGLE, now.minusSeconds(1), now);

        assertThat(valid.isValidAt(now)).isTrue();
        assertThat(expired.isValidAt(now)).isFalse();
    }
}
