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

    @AssertTrue(message = "{signup.error.terms.service}")
    private boolean termsAccepted;

    @AssertTrue(message = "{signup.error.terms.privacy}")
    private boolean privacyAccepted;
}
