package com.example.travlediary.service.user;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AccountReauthenticationServiceTest {

    private final AccountReauthenticationService service =
            new AccountReauthenticationService();

    @Test
    void verificationIsBoundToUserAndValidForTenMinutes() {
        MockHttpSession session = new MockHttpSession();
        service.markVerified(session, 7L);

        assertThat(service.isVerified(session, 7L)).isTrue();
        assertThat(service.isVerified(session, 8L)).isFalse();
    }

    @Test
    void expiredVerificationIsRejectedWithoutSleeping() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AccountReauthenticationService.VERIFIED_USER_ID, 7L);
        session.setAttribute(AccountReauthenticationService.VERIFIED_AT,
                Instant.now().minusSeconds(601));

        assertThat(service.isVerified(session, 7L)).isFalse();
        assertThat(session.getAttribute(AccountReauthenticationService.VERIFIED_USER_ID))
                .isNull();
        assertThat(session.getAttribute(AccountReauthenticationService.VERIFIED_AT))
                .isNull();
    }
}
