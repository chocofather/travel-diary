package com.example.travlediary.service.user;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class AccountReauthenticationService {

    static final String VERIFIED_USER_ID = "ACCOUNT_VERIFIED_USER_ID";
    static final String VERIFIED_AT = "ACCOUNT_VERIFIED_AT";
    static final Duration VALIDITY = Duration.ofMinutes(10);

    public void markVerified(HttpSession session, Long userId) {
        session.setAttribute(VERIFIED_USER_ID, userId);
        session.setAttribute(VERIFIED_AT, Instant.now());
    }

    public boolean isVerified(HttpSession session, Long userId) {
        Object storedUserId = session.getAttribute(VERIFIED_USER_ID);
        Object storedAt = session.getAttribute(VERIFIED_AT);
        if (!(storedUserId instanceof Long verifiedUserId)
                || !(storedAt instanceof Instant verifiedAt)
                || !verifiedUserId.equals(userId)) {
            clear(session);
            return false;
        }

        Instant now = Instant.now();
        boolean valid = !verifiedAt.isAfter(now)
                && !verifiedAt.plus(VALIDITY).isBefore(now);
        if (!valid) {
            clear(session);
        }
        return valid;
    }

    public void clear(HttpSession session) {
        session.removeAttribute(VERIFIED_USER_ID);
        session.removeAttribute(VERIFIED_AT);
    }
}
