package com.example.travlediary.service.user;

import com.example.travlediary.model.SocialProvider;

/**
 * 최종 파기의 정책 차이만 담는다. 파기 절차 자체는 두 경우 모두 같다.
 *
 * <p>자동 배치({@link #scheduled()})는 남아 있는 provider 연결을 우리가 대신 끊어 줘야 하므로
 * 지금까지처럼 SOCIAL_UNLINK task 를 남긴다.
 *
 * <p>유예가 끝난 계정으로 사용자가 직접 다시 소셜 로그인해서 즉시 파기하는 경우
 * ({@link #reauthenticated(SocialProvider)})는 다르다. 그 provider 계정은 사용자가 바로
 * 새 가입에 다시 쓸 참이라, 나중에 실행되는 unlink worker 가 앱 연결을 끊어 버리면
 * 새로 만든 계정의 연결까지 같이 날아간다. 그래서 그 provider 만 task 를 만들지 않는다.
 */
public record AccountPurgeOptions(SocialProvider reauthenticatedProvider) {

    /** 유예가 끝난 회원을 배치가 자동으로 파기하는 경우. 기존 정책 그대로다. */
    public static AccountPurgeOptions scheduled() {
        return new AccountPurgeOptions(null);
    }

    /** 그 provider 로 방금 본인 인증을 마치고 즉시 파기하는 경우. */
    public static AccountPurgeOptions reauthenticated(SocialProvider provider) {
        return new AccountPurgeOptions(provider);
    }

    /** 방금 인증에 쓴 provider 는 연결 해제 task 를 만들지 않는다. */
    public boolean suppressesUnlinkFor(SocialProvider provider) {
        return reauthenticatedProvider != null && reauthenticatedProvider == provider;
    }
}
