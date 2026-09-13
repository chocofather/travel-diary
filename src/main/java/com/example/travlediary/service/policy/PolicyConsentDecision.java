package com.example.travlediary.service.policy;

import com.example.travlediary.model.PolicyType;

/**
 * user_policy_consents 에 남길 결정 한 건. 동의뿐 아니라 거절도 결정이라 그대로 저장한다.
 *
 * <p>policyVersionId 는 화면이 보낸 값이 아니라 서버가 다시 조회한 현재 정책 세트의 id 다.
 *
 * @param policyVersionId 결정 대상 policy_versions.id
 * @param type 로그/판독용 정책 종류
 * @param agreed 동의했으면 true, 선택 항목을 체크하지 않았으면 false
 */
public record PolicyConsentDecision(Long policyVersionId, PolicyType type, boolean agreed) {
}
