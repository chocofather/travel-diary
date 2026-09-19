package com.example.travlediary.service.policy;

import com.example.travlediary.model.PolicyType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 활성화 후의 정책 세트를 흉내 내는 테스트 고정값.
 * requires_consent / is_required 는 DB 에 등록된 v1.0 구성과 같게 둔다.
 */
public final class SignupPolicyFixtures {

    public static final long TERMS_ID = 101L;
    public static final long PRIVACY_POLICY_ID = 102L;
    public static final long PRIVACY_COLLECTION_ID = 103L;
    public static final long MARKETING_ID = 104L;

    private SignupPolicyFixtures() {
    }

    /** TERMS(필수) / PRIVACY_COLLECTION(필수) / MARKETING(선택) / PRIVACY_POLICY(열람 전용). */
    public static SignupPolicySet activeSignupPolicies() {
        return new SignupPolicySet(List.of(
                policy(TERMS_ID, PolicyType.TERMS_OF_SERVICE, true, true),
                policy(PRIVACY_COLLECTION_ID, PolicyType.PRIVACY_COLLECTION, true, true),
                policy(MARKETING_ID, PolicyType.MARKETING_EMAIL, true, false),
                policy(PRIVACY_POLICY_ID, PolicyType.PRIVACY_POLICY, false, false)));
    }

    /** 필수 동의 두 건만 담은 목록. 선택 항목은 빠져 있다. */
    public static List<Long> requiredConsentIds() {
        return List.of(TERMS_ID, PRIVACY_COLLECTION_ID);
    }

    public static SignupPolicy policy(long id, PolicyType type,
                                      boolean requiresConsent, boolean required) {
        return new SignupPolicy(id, type, "1.0", requiresConsent, required,
                type.name() + " 제목", "<p>" + type.name() + " 본문</p>", "ko",
                LocalDateTime.of(2026, 9, 1, 0, 0));
    }
}
