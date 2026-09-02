package com.example.travlediary.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SocialSignupForm {

    @NotBlank(message = "닉네임을 입력해주세요.")
    @Size(min = 2, max = 12, message = "닉네임은 2~12자여야 합니다.")
    private String nickname;

    @AssertTrue(message = "서비스 이용약관에 동의해주세요.")
    private boolean termsAccepted;

    @AssertTrue(message = "개인정보 수집 및 이용에 동의해주세요.")
    private boolean privacyAccepted;
}
