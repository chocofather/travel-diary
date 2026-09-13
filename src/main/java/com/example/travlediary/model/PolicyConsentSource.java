package com.example.travlediary.model;

/**
 * user_policy_consents.consent_source. DB 의 chk_user_policy_consent_source 와 값이 같아야 한다.
 * 다른 값을 넣으면 CHECK 제약에서 INSERT 가 거부된다.
 */
public enum PolicyConsentSource {

    /** 일반 회원가입 화면 */
    SIGNUP,
    /** 소셜 신규가입 화면 */
    SOCIAL_SIGNUP,
    /** 마이페이지에서 나중에 바꾼 동의 */
    MYPAGE,
    /** 새 버전 재동의 */
    RECONSENT
}
