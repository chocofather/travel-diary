package com.example.travlediary.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SocialSignupForm {

    // 길이/문구 정책은 일반 회원가입(RegistrationForm)과 같은 key 를 쓴다.
    @NotBlank(message = "{signup.error.nickname.required}")
    @Size(min = 2, max = 16, message = "{signup.error.nickname.size}")
    private String nickname;

    /**
     * Travel Diary 이메일 인증이 필요한 provider(Kakao/Naver)에서만 쓰는 입력값.
     * Google 은 provider 가 인증한 이메일을 쓰므로 이 값을 보내도 무시한다.
     * 필수 여부와 형식은 provider 별 정책이라 Bean Validation 이 아니라 서비스에서 본다.
     */
    @Size(max = 100, message = "{signup.error.email.invalid}")
    private String userEmail;

    @AssertTrue(message = "{signup.error.terms.service}")
    private boolean termsAccepted;

    @AssertTrue(message = "{signup.error.terms.privacy}")
    private boolean privacyAccepted;
}
