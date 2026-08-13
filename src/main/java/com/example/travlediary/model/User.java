package com.example.travlediary.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class User {
    private Long id; // 회원 ID값
    private String username; // 회원 ID
    private String userPassword; // 비밀번호

    // 개인정보
    private String userEmail; // 이메일
    private String fullName; // 회원 이름
    private String userPhone; // 회원 전화번호
    private LocalDate userBirth; // 회원 생년월일
    private String profileImage; // 회원 이미지 DB에 저장될 파일 경로

    private transient MultipartFile profileImageFile; // DB 저장에서 제외 (transient 키워드 사용)

    // 기타 정보
    private String nickname; // 닉네임
    private UserRole userRole; // 역할 관리자, 유저
   // private Status status; // 회원 상태
    private UserStatus status = UserStatus.INACTIVE;

    // 날짜 관련
    private Timestamp createdAt; // 생성일
    private Timestamp updatedAt; // 수정일
    private Timestamp deletedAt; // 탈퇴일
    private Timestamp lastLogin; // 마지막 로그인

    // 메일인증
    private String verificationToken; // 메일 인증 토큰
    private LocalDateTime verificationTokenExp; // 메일 인증 토큰 만료 시각
    private LocalDateTime verificationRequestedAt; // 마지막 인증메일 발송 요청 시각
    private String resetToken;               // 비밀번호 재설정 토큰
    private LocalDateTime resetTokenExp; // 만료 시각


    public String getRole() {
        return this.userRole != null ? this.userRole.name() : null;
    }

}
