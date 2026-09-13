package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * user_policy_consents 한 행. 동의뿐 아니라 거절(agreed = false)도 결정 이력으로 남는다.
 * append-only 라 이 모델로 UPDATE 하지 않는다.
 */
@Data
@NoArgsConstructor
public class UserPolicyConsent {

    private Long id;
    private Long userId;
    private Long policyVersionId;
    private boolean agreed;
    /** 동의 당시 화면 locale. ko/en/ja/zh-CN/zh-TW 만 허용된다. */
    private String locale;
    private PolicyConsentSource consentSource;
}
