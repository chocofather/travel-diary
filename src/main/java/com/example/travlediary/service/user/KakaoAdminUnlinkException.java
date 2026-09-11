package com.example.travlediary.service.user;

/**
 * Admin Key 기반 카카오 연결 해제 실패.
 *
 * <p>{@link Kind}가 재시도 여부를 정한다. 메시지에는 Admin Key, Authorization 헤더,
 * provider 사용자 식별값, 응답 본문을 담지 않는다. last_error 에 그대로 저장되기 때문이다.
 */
public class KakaoAdminUnlinkException extends RuntimeException {

    private final Kind kind;

    public KakaoAdminUnlinkException(Kind kind) {
        super(kind.errorCode());
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    /** 다시 시도하면 결과가 달라질 수 있는 실패인지. */
    public boolean isRetryable() {
        return kind.retryable;
    }

    /** last_error 에 남길 안전한 분류값. */
    public String errorCode() {
        return kind.errorCode();
    }

    public enum Kind {
        /** Admin Key 가 설정되지 않았다. 운영에서 값을 넣으면 다음 시도에 풀린다. */
        CONFIGURATION(true, "카카오 Admin Key 가 설정되지 않았습니다."),
        /** task 의 target_value 가 카카오 회원번호 형식이 아니다. 다시 시도해도 같다. */
        INVALID_PROVIDER_USER_ID(false, "카카오 회원번호 형식이 아닙니다."),
        /** 네트워크 오류나 타임아웃. */
        TIMEOUT(true, "카카오 API 응답을 받지 못했습니다."),
        /** 카카오 쪽 오류(5xx). 잠시 뒤 다시 시도한다. */
        HTTP_5XX(true, "카카오 API 가 오류를 반환했습니다."),
        /**
         * 4xx. 이미 해제된 회원인지, 권한/키 문제인지, 없는 회원번호인지를
         * 공식 오류코드 대조 없이 구분할 수 없어 성공으로 넘기지 않는다.
         * 운영자가 직접 확인하도록 남긴다.
         */
        HTTP_4XX(false, "카카오 API 가 요청을 거부했습니다. 확인이 필요합니다."),
        /** 응답이 비었거나 돌려받은 id 가 요청한 회원번호와 다르다. */
        RESPONSE_MISMATCH(false, "카카오 API 응답을 확인하지 못했습니다.");

        private final boolean retryable;
        private final String description;

        Kind(boolean retryable, String description) {
            this.retryable = retryable;
            this.description = description;
        }

        private String errorCode() {
            return name() + ": " + description;
        }
    }
}
