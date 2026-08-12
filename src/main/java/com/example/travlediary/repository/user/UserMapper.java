package com.example.travlediary.repository.user;

import com.example.travlediary.dto.MyPageProfileDto;
import com.example.travlediary.dto.PublicUserProfileDto;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface UserMapper {

    /* ---------- 회원가입 & 조회 ---------- */
    void insertUser(User user);
    User findByUsername(@Param("username") String username);
    User findById(Long id);
    PublicUserProfileDto findPublicProfileById(@Param("id") Long id);
    String findProfileImageByUsername(@Param("username") String username);
    MyPageProfileDto findMyPageProfileById(@Param("id") Long id);
    MyPageProfileDto findMyPageProfileByIdForUpdate(@Param("id") Long id);

    /* ---------- 관리자 역할 변경 ---------- */
    void updateUserRole(@Param("id") Long id,      // ★ Integer → Long
                        @Param("userRole") UserRole userRole);

    /* ---------- 비밀번호 변경 ---------- */
    void updateUserPassword(@Param("id") Long id,  // ★ Integer → Long
                            @Param("userPassword") String userPassword);

    /* ---------- 중복 체크 ---------- */
    int countByUsername(String username);
    int countByNickname(String nickname);
    int countByNicknameExcludingUserId(@Param("nickname") String nickname,
                                       @Param("userId") Long userId);

    int updateMyPageProfile(@Param("userId") Long userId,
                            @Param("nickname") String nickname,
                            @Param("profileImage") String profileImage);

    /* ---------- 이메일 인증 ---------- */
    User findByVerificationToken(String token);
    void updateUser(User user);
    User findByEmail(String email);

    /* ---------- 아이디/비밀번호 찾기 ---------- */
    User findByFullNameAndEmail(@Param("fullName") String fullName,
                                @Param("userEmail") String userEmail);

    User findByUsernameAndEmail(@Param("username") String username,
                                @Param("userEmail") String userEmail);

    /* ---------- 재설정 토큰 ---------- */
    void updateResetToken(@Param("id") Long id,
                          @Param("token") String token,
                          @Param("expiresAt") LocalDateTime expiresAt);

    User findByResetToken(String token);
    void clearResetToken(Long id);                 // 이미 Long ✔
}
