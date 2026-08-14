package com.example.travlediary.dto;

import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import lombok.Data;

import java.sql.Timestamp;

/** 관리자 회원 목록 한 줄에 필요한 정보만 담는다. */
@Data
public class AdminUserListItemDto {
    private Long id;
    private String username;
    private String nickname;
    private String userEmail;
    private UserRole userRole;
    private UserStatus status;
    private Timestamp createdAt;

    /** 관리자 계정은 이후 제재(정지/강제탈퇴) 대상에서 제외한다. */
    public boolean isAdmin() {
        return userRole == UserRole.ADMIN;
    }
}