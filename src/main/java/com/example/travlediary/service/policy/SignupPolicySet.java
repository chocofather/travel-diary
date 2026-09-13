package com.example.travlediary.service.policy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 한 번의 가입에 쓰이는 정책 묶음. 화면 렌더링과 서버 검증이 같은 인스턴스를 본다.
 *
 * <p>아직 활성화하지 않은 정책은 애초에 들어오지 않는다. 그래서 활성화 전에는 비어 있고,
 * 그 상태에서는 필수 동의도 없어 기존 가입 흐름이 그대로 동작한다.
 */
public record SignupPolicySet(List<SignupPolicy> policies) {

    public SignupPolicySet {
        policies = policies == null ? List.of() : List.copyOf(policies);
    }

    public static SignupPolicySet empty() {
        return new SignupPolicySet(List.of());
    }

    public boolean isEmpty() {
        return policies.isEmpty();
    }

    /** 동의 체크박스를 만드는 정책. 필수/선택이 섞여 있고 화면 순서를 그대로 따른다. */
    public List<SignupPolicy> consentPolicies() {
        return policies.stream().filter(SignupPolicy::requiresConsent).toList();
    }

    /** 동의 대상이 아니라 열람만 제공하는 정책(개인정보처리방침 등). */
    public List<SignupPolicy> viewOnlyPolicies() {
        return policies.stream().filter(policy -> !policy.requiresConsent()).toList();
    }

    /**
     * 화면이 보낸 동의 id 를 현재 정책 세트로 해석해 저장할 결정 목록을 만든다.
     *
     * <p>세트에 없는 id 는 전부 버린다. 예전 버전이나 존재하지 않는 id 를 보내도
     * 그 정책에 동의한 것으로 처리되지 않고, 필수 동의를 대신 채우지도 못한다.
     *
     * <p>열람 전용 정책(requires_consent = 0)은 결정 자체를 만들지 않으므로
     * user_policy_consents 에 행이 생기지 않는다.
     *
     * @param agreedPolicyVersionIds 화면에서 체크된 policy_versions.id (신뢰하지 않는 입력)
     * @return 동의/거절 결정. 동의 체크박스가 있는 정책 수와 같다
     * @throws PolicyConsentRequiredException 필수 동의가 빠진 경우
     */
    public List<PolicyConsentDecision> decide(Collection<Long> agreedPolicyVersionIds) {
        Set<Long> submitted = new HashSet<>();
        if (agreedPolicyVersionIds != null) {
            for (Long id : agreedPolicyVersionIds) {
                if (id != null) {
                    submitted.add(id);
                }
            }
        }

        List<PolicyConsentDecision> decisions = new ArrayList<>();
        for (SignupPolicy policy : consentPolicies()) {
            boolean agreed = submitted.contains(policy.policyVersionId());
            if (!agreed && policy.mandatory()) {
                throw new PolicyConsentRequiredException(policy);
            }
            decisions.add(new PolicyConsentDecision(
                    policy.policyVersionId(), policy.type(), agreed));
        }
        return decisions;
    }
}
