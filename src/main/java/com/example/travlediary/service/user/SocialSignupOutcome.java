package com.example.travlediary.service.user;

/**
 * 소셜 가입 트랜잭션의 결과.
 *
 * @param userId            새로 만든 회원 id
 * @param userEmail         users.user_email 에 저장한 이메일
 * @param verificationToken Travel Diary 이메일 인증이 필요한 경우의 발급 토큰.
 *                          provider 가 이미 인증한 이메일이면 null 이고 계정은 바로 ACTIVE 다.
 */
public record SocialSignupOutcome(long userId, String userEmail, String verificationToken) {

    public boolean requiresEmailVerification() {
        return verificationToken != null;
    }
}
