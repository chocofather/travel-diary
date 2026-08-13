package com.example.travlediary.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.example.travlediary.service.user.FullNamePolicy;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class RegistrationForm {

    @AssertTrue(message = "서비스 이용약관에 동의해주세요.")
    private boolean serviceTermsAccepted;

    @AssertTrue(message = "개인정보 수집 및 이용에 동의해주세요.")
    private boolean privacyTermsAccepted;

    @NotBlank(message = "아이디를 입력해주세요.")
    @Pattern(regexp = "^(?=.*[a-z])[a-z0-9_-]{3,16}$",
            message = "아이디는 영문 소문자를 포함한 3~16자의 영문, 숫자, -, _만 사용할 수 있습니다.")
    private String username;

    @NotBlank(message = "이메일 주소를 입력해주세요.")
    @Email(message = "올바른 이메일 주소를 입력해주세요.")
    @Size(max = 100, message = "이메일 주소는 100자 이하여야 합니다.")
    private String userEmail;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    private String userPassword;

    @NotBlank(message = "비밀번호 확인을 입력해주세요.")
    private String passwordConfirm;

    @NotBlank(message = "닉네임을 입력해주세요.")
    @Size(min = 2, max = 12, message = "닉네임은 2~12자여야 합니다.")
    private String nickname;

    @NotBlank(message = "이름을 입력해주세요.")
    @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
    @Pattern(regexp = FullNamePolicy.INPUT_PATTERN,
            message = FullNamePolicy.INVALID_MESSAGE)
    private String fullName;

    @Size(max = 20, message = "전화번호는 20자 이하여야 합니다.")
    @Pattern(regexp = "^$|^[0-9-]+$", message = "전화번호는 숫자와 하이픈만 입력할 수 있습니다.")
    private String userPhone;

    @NotNull(message = "생년월일을 선택해주세요.")
    @PastOrPresent(message = "생년월일은 오늘 이후일 수 없습니다.")
    private LocalDate userBirth;

    private MultipartFile profileImageFile;

    @AssertTrue(message = "비밀번호가 일치하지 않습니다.")
    public boolean isPasswordConfirmed() {
        return userPassword != null && userPassword.equals(passwordConfirm);
    }
}
