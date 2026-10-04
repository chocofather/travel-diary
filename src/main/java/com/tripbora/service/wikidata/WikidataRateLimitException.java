package com.tripbora.service.wikidata;

import java.time.Duration;

/** Wikidata API·지역 조회(WDQS)가 요청 제한으로 재시도 한도 안에 풀리지 않았다. 일시적이라 잠시 뒤 다시 할 수 있다. */
public class WikidataRateLimitException extends WikidataApiException implements ExternalApiRateLimiter.RateLimited {
    private final ExternalApiRateLimiter.Service service;
    private final transient Duration retryAfter;

    public WikidataRateLimitException(ExternalApiRateLimiter.Service service, Duration retryAfter) {
        super(RateLimitMessages.message(service, retryAfter));
        this.service = service;
        this.retryAfter = retryAfter;
    }

    @Override
    public ExternalApiRateLimiter.Service service() {
        return service;
    }

    @Override
    public Duration retryAfter() {
        return retryAfter;
    }
}
