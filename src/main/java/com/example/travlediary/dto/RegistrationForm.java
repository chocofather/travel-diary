package com.example.travlediary.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RegistrationForm {

    @AssertTrue(message = "{signup.error.terms.service}")
    private boolean serviceTermsAccepted;

    @AssertTrue(message = "{signup.error.terms.privacy}")
    private boolean privacyTermsAccepted;

    @NotBlank(message = "{signup.error.username.required}")
    @Pattern(regexp = "^(?=.*[a-z])[a-z0-9_-]{3,16}$",
            message = "{signup.error.username.pattern}")
    private String username;

    @NotBlank(message = "{signup.error.email.required}")
    @Email(message = "{signup.error.email.invalid}")
    @Size(max = 100, message = "{signup.error.email.tooLong}")
    private String userEmail;

    @NotBlank(message = "{signup.error.password.required}")
    private String userPassword;

    @NotBlank(message = "{signup.error.passwordConfirm.required}")
    private String passwordConfirm;

    @NotBlank(message = "{signup.error.nickname.required}")
    @Size(min = 2, max = 16, message = "{signup.error.nickname.size}")
    private String nickname;

    @AssertTrue(message = "{signup.error.passwordConfirm.mismatch}")
    public boolean isPasswordConfirmed() {
        return userPassword != null && userPassword.equals(passwordConfirm);
    }
}
