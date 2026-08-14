package com.example.travlediary.model;

public enum UserStatus {
    // INACTIVE ( 이메일 인증 미완료 회원)
    // ACTIVE( 정상적으로 사용 중인 회원 )
    // SUSPENDED (휴먼 계정 1년 이상 로그인 안 한 회원)
    // DEACTIVATED (탈퇴한 회원 Soft Delete 처리)
    // RESTRICTED (관리자 제재로 이용이 정지된 회원, 기간·영구 포함)
    INACTIVE("인증대기"),
    ACTIVE("정상"),
    SUSPENDED("휴면"),
    DEACTIVATED("탈퇴"),
    RESTRICTED("이용정지");

    private final String displayName;

    UserStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
