package com.example.travlediary.security;

/**
 * 회원 관련 공개 endpoint 의 요청 한도를 넘었을 때 나는 예외.
 *
 * <p>담는 것은 "얼마 뒤에 다시 시도할 수 있는지" 하나뿐이다. 어떤 한도에 걸렸는지, 어떤 키로
 * 세고 있는지는 응답으로 내보내지 않는다.
 */
public class TooManyAccountRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyAccountRequestsException(long retryAfterSeconds) {
        super("요청이 너무 많습니다.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
