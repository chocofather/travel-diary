package com.example.travlediary.repository.user;

import com.example.travlediary.dto.AccountDetailsDto;
import com.example.travlediary.dto.AdminUserDetailDto;
import com.example.travlediary.dto.AdminUserListItemDto;
import com.example.travlediary.dto.MyPageProfileDto;
import com.example.travlediary.dto.PublicUserProfileDto;
import com.example.travlediary.model.User;
import com.example.travlediary.model.UserRole;
import com.example.travlediary.model.UserStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
    AccountDetailsDto findAccountDetailsById(@Param("id") Long id);
    User findActiveAccountSecurityById(@Param("id") Long id);
    User findActiveAccountSecurityByIdForUpdate(@Param("id") Long id);

    /* ---------- 관리자 회원 조회 ---------- */
    long countAdminUsers(@Param("keyword") String keyword,
                         @Param("status") UserStatus status);

    List<AdminUserListItemDto> findAdminUsers(@Param("keyword") String keyword,
                                              @Param("status") UserStatus status,
                                              @Param("offset") long offset,
                                              @Param("limit") int limit);

    AdminUserDetailDto findAdminUserById(@Param("id") Long id);

    /* ---------- 관리자 역할 변경 ---------- */
    void updateUserRole(@Param("id") Long id,      // ★ Integer → Long
                        @Param("userRole") UserRole userRole);

    /* ---------- 비밀번호 변경 ---------- */
    void updateUserPassword(@Param("id") Long id,  // ★ Integer → Long
                            @Param("userPassword") String userPassword);
    int updateActiveUserPassword(@Param("id") Long id,
                                 @Param("userPassword") String userPassword);

    int updateAccountDetails(@Param("id") Long id,
                             @Param("fullName") String fullName,
                             @Param("userPhone") String userPhone,
                             @Param("userBirth") LocalDate userBirth);

    int deactivateAccount(@Param("id") Long id,
                          @Param("userEmail") String userEmail,
                          @Param("nickname") String nickname,
                          @Param("status") UserStatus status);

    /* ---------- 중복 체크 ---------- */
    int countByUsername(String username);
    int countByNickname(String nickname);
    int countByNicknameExcludingUserId(@Param("nickname") String nickname,
                                       @Param("userId") Long userId);

    int updateMyPageProfile(@Param("userId") Long userId,
                            @Param("nickname") String nickname,
                            @Param("profileImage") String profileImage);

    /* ---------- 이메일 인증 ---------- */
    User findByEmail(String email);
    User findPendingVerificationByToken(@Param("token") String token);
    User findPendingVerificationByEmail(@Param("userEmail") String userEmail);
    int activatePendingUser(@Param("id") Long id,
                            @Param("token") String token,
                            @Param("verifiedAt") LocalDateTime verifiedAt);
    int refreshVerificationToken(@Param("id") Long id,
                                 @Param("token") String token,
                                 @Param("expiresAt") LocalDateTime expiresAt,
                                 @Param("requestedAt") LocalDateTime requestedAt,
                                 @Param("cooldownCutoff") LocalDateTime cooldownCutoff);

    /* ---------- 아이디/비밀번호 찾기 ---------- */
    User findActiveByEmailForUsernameRecovery(@Param("userEmail") String userEmail);

    User findByUsernameAndEmail(@Param("username") String username,
                                @Param("userEmail") String userEmail);

    /* ---------- 재설정 토큰 ---------- */
    void updateResetToken(@Param("id") Long id,
                          @Param("tokenHash") String tokenHash,
                          @Param("expiresAt") LocalDateTime expiresAt);

    User findByResetToken(@Param("tokenHash") String tokenHash);
    void clearResetToken(Long id);                 // 이미 Long ✔
}
