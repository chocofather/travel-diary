package com.example.travlediary.service.translation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class TranslationProperties {
    private final int maxCommentCharacters;
    private final Duration leaseDuration;
    private final Duration failureRetryAfter;
    private final int ipRequests;
    private final int userRequests;
    private final int externalRequests;
    private final Duration rateLimitWindow;
    private final boolean monthlyCharacterLimitEnabled;
    private final long monthlyCharacterLimit;
    private final long authenticatedDailyCharacterLimit;
    private final long anonymousDailyCharacterLimit;

    public TranslationProperties(
            @Value("${translation.comment.max-characters:2000}") int maxCommentCharacters,
            @Value("${translation.lease-duration:30s}") Duration leaseDuration,
            @Value("${translation.failure-retry-after:30s}") Duration failureRetryAfter,
            @Value("${translation.rate-limit.ip-requests:30}") int ipRequests,
            @Value("${translation.rate-limit.user-requests:20}") int userRequests,
            @Value("${translation.rate-limit.external-requests:10}") int externalRequests,
            @Value("${translation.rate-limit.window:1m}") Duration rateLimitWindow,
            @Value("${translation.monthly-character-limit-enabled:true}") boolean monthlyCharacterLimitEnabled,
            @Value("${translation.monthly-character-limit}") long monthlyCharacterLimit,
            @Value("${translation.daily-character-limit.authenticated}") long authenticatedDailyCharacterLimit,
            @Value("${translation.daily-character-limit.anonymous}") long anonymousDailyCharacterLimit) {
        this.maxCommentCharacters = maxCommentCharacters;
        this.leaseDuration = leaseDuration;
        this.failureRetryAfter = failureRetryAfter;
        this.ipRequests = ipRequests;
        this.userRequests = userRequests;
        this.externalRequests = externalRequests;
        this.rateLimitWindow = rateLimitWindow;
        this.monthlyCharacterLimitEnabled = monthlyCharacterLimitEnabled;
        this.monthlyCharacterLimit = monthlyCharacterLimit;
        this.authenticatedDailyCharacterLimit = authenticatedDailyCharacterLimit;
        this.anonymousDailyCharacterLimit = anonymousDailyCharacterLimit;
    }

    public int maxCommentCharacters() { return maxCommentCharacters; }
    public Duration leaseDuration() { return leaseDuration; }
    public Duration failureRetryAfter() { return failureRetryAfter; }
    public int ipRequests() { return ipRequests; }
    public int userRequests() { return userRequests; }
    public int externalRequests() { return externalRequests; }
    public Duration rateLimitWindow() { return rateLimitWindow; }
    public boolean monthlyCharacterLimitEnabled() { return monthlyCharacterLimitEnabled; }
    public long monthlyCharacterLimit() { return monthlyCharacterLimit; }
    public long authenticatedDailyCharacterLimit() { return authenticatedDailyCharacterLimit; }
    public long anonymousDailyCharacterLimit() { return anonymousDailyCharacterLimit; }
}
