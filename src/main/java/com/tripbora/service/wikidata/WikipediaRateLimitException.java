package com.tripbora.service.wikidata;

import java.time.Duration;

/** Wikipedia API가 요청 제한으로 재시도 한도 안에 풀리지 않았다. 일시적이라 잠시 뒤 다시 할 수 있다. */
public class WikipediaRateLimitException extends WikipediaApiException implements ExternalApiRateLimiter.RateLimited {
    private final transient Duration retryAfter;

    public WikipediaRateLimitException(Duration retryAfter) {
        super(RateLimitMessages.message(ExternalApiRateLimiter.Service.WIKIPEDIA, retryAfter));
        this.retryAfter = retryAfter;
    }

    @Override
    public ExternalApiRateLimiter.Service service() {
        return ExternalApiRateLimiter.Service.WIKIPEDIA;
    }

    @Override
    public Duration retryAfter() {
        return retryAfter;
    }
}
