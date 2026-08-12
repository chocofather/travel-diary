package com.example.travlediary.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
public class AccountDetailsDto {
    private String username;
    private String userEmail;
    private String fullName;
    private String userPhone;
    private LocalDate userBirth;
}
