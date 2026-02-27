package com.example.travlediary.model;

public enum UserStatus {
    // INACTIVE ( 이메일 인증 미완료 회원)
    // ACTIVE( 정상적으로 사용 중인 회원 )
    // SUSPENDED (휴먼 계정 1년 이상 로그인 안 한 회원)
    // DEACTIVATED (탈퇴한 회원 Soft Delete 처리)
    INACTIVE, ACTIVE, SUSPENDED, DEACTIVATED
}
