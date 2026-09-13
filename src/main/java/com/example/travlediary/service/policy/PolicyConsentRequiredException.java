package com.example.travlediary.service.policy;

import com.example.travlediary.model.PolicyType;

/**
 * 필수 동의가 빠진 채 가입 POST 가 들어왔다. 이 예외가 나면 users INSERT 를 하지 않는다.
 *
 * <p>일반 가입과 소셜 가입의 예외 타입이 서로 달라, 각 서비스가 이 예외를 받아
 * 자기 화면의 검증 예외로 바꿔 던진다.
 */
public class PolicyConsentRequiredException extends RuntimeException {

    private final transient PolicyType policyType;
    private final String messageCode;

    public PolicyConsentRequiredException(SignupPolicy policy) {
        super("필수 약관 동의가 필요합니다: " + policy.type());
        this.policyType = policy.type();
        this.messageCode = policy.consentRequiredCode();
    }

    public PolicyType getPolicyType() {
        return policyType;
    }

    /** 현재 locale 의 messages 번들에서 찾을 문구 key. */
    public String getMessageCode() {
        return messageCode;
    }
}
