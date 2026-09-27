package com.example.travlediary.service.wikidata;

import java.time.Duration;

/** 요청 제한 안내 문구. 관리자 화면은 한국어만 쓴다. */
final class RateLimitMessages {
    private RateLimitMessages() {
    }

    static String message(ExternalApiRateLimiter.Service service, Duration retryAfter) {
        long seconds = retryAfter == null ? 0 : Math.max(1, (retryAfter.toMillis() + 999) / 1000);
        return service.label() + " 요청이 많습니다. 잠시 후 다시 시도해 주세요."
                + (seconds > 0 ? " (약 " + seconds + "초 뒤 가능)" : "");
    }

    static String forbidden(String serviceLabel) {
        return serviceLabel + "가 요청을 거부했습니다(HTTP 403). 반복되면 서버의 접속 제한이나 요청 식별 정보(User-Agent)를 확인해 주세요.";
    }
}
