package com.example.travlediary.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PasswordChangeForm {
    private String newPassword;
    private String newPasswordConfirm;
}
