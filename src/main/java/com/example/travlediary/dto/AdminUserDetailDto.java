package com.example.travlediary.dto;

import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import lombok.Data;

import java.sql.Timestamp;
import java.time.LocalDate;

/** 관리자 회원 상세에서 확인하는 기존 users 데이터. */
@Data
public class AdminUserDetailDto {
    private Long id;
    private String username;
    private String nickname;
    private String userEmail;
    private String fullName;
    private String userPhone;
    private LocalDate userBirth;
    private String profileImage;
    private UserRole userRole;
    private UserStatus status;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp lastLogin;
    private Timestamp deletedAt;

    /** 관리자 계정은 이후 제재(정지/강제탈퇴) 대상에서 제외한다. */
    public boolean isAdmin() {
        return userRole == UserRole.ADMIN;
    }
}