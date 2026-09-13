package com.example.travlediary.service.policy;

import com.example.travlediary.model.PolicyType;

/**
 * 회원가입 화면에 내보낼 정책 한 건. 화면과 서버 검증이 같은 값을 본다.
 *
 * @param policyVersionId 동의 이력에 저장될 실제 policy_versions.id
 * @param type 정책 종류
 * @param version 표시/로그용 버전 문자열
 * @param requiresConsent 동의 체크박스를 만드는 정책인지. false 면 열람만 제공한다
 * @param required 필수 동의인지. 화면 문자열이 아니라 policy_versions.is_required 가 기준이다
 * @param title 번역 제목. 해당 버전에 번역이 아예 없으면 null
 * @param content 번역 본문. 번역이 아예 없으면 null
 * @param contentLocale 실제로 사용한 번역 locale. 대체 locale 을 썼으면 요청 locale 과 다르다
 */
public record SignupPolicy(
        Long policyVersionId,
        PolicyType type,
        String version,
        boolean requiresConsent,
        boolean required,
        String title,
        String content,
        String contentLocale) {

    /** 번역 제목이 아직 없을 때 화면이 대신 쓸 messages key. */
    public String labelCode() {
        return type.getLabelCode();
    }

    /** 필수 동의가 빠졌을 때 보여줄 messages key. */
    public String consentRequiredCode() {
        return type.getConsentRequiredCode();
    }

    /** 서버가 동의를 강제해야 하는 정책. 열람 전용(requires_consent = 0)은 제외된다. */
    public boolean mandatory() {
        return requiresConsent && required;
    }
}
