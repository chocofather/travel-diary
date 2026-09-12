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

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface UserMapper {

    /* ---------- 회원가입 & 조회 ---------- */
    void insertUser(User user);
    User findByUsername(@Param("username") String username);
    User findById(Long id);
    PublicUserProfileDto findPublicProfileById(@Param("id") Long id);
    MyPageProfileDto findMyPageProfileById(@Param("id") Long id);
    MyPageProfileDto findMyPageProfileByIdForUpdate(@Param("id") Long id);
    AccountDetailsDto findAccountDetailsById(@Param("id") Long id);
    boolean hasLocalPasswordById(@Param("id") Long id);
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

    /** 관리자 조치용 잠금 조회. 상태와 무관하게 회원을 가져온다. */
    User findByIdForUpdate(@Param("id") Long id);

    /** 요청마다 현재 상태만 확인할 때 사용하는 가벼운 조회. */
    UserStatus findStatusById(@Param("id") Long id);

    /** 관리자 조치로 회원 상태만 변경한다. */
    int updateStatusForAdmin(@Param("id") Long id,
                             @Param("status") UserStatus status,
                             @Param("expectedStatus") UserStatus expectedStatus);

    /* ---------- 관리자 역할 변경 ---------- */
    void updateUserRole(@Param("id") Long id,      // ★ Integer → Long
                        @Param("userRole") UserRole userRole);

    /* ---------- 비밀번호 변경 ---------- */
    void updateUserPassword(@Param("id") Long id,  // ★ Integer → Long
                            @Param("userPassword") String userPassword);
    int updateActiveUserPassword(@Param("id") Long id,
                                 @Param("userPassword") String userPassword);

    /** 마이페이지 회원정보 수정. 생년월일은 이 화면에서 바꾸지 않으므로 갱신하지 않는다. */
    int updateAccountDetails(@Param("id") Long id,
                             @Param("fullName") String fullName,
                             @Param("userPhone") String userPhone);

    /**
     * 탈퇴 신청. 상태와 유예 일정만 기록하고 개인정보/콘텐츠는 전혀 건드리지 않는다.
     * 최종 파기(익명화)는 별도의 deactivateAccount 가 담당한다.
     */
    int requestWithdrawal(@Param("id") Long id,
                          @Param("status") UserStatus status,
                          @Param("requestedAt") LocalDateTime requestedAt,
                          @Param("purgeScheduledAt") LocalDateTime purgeScheduledAt);

    /**
     * 탈퇴 유예 안내 화면용 조회. 유예기간이 이미 끝난 회원도 화면은 봐야 하므로
     * 날짜 조건 없이 상태만으로 찾는다.
     */
    User findWithdrawalPendingById(@Param("id") Long id);

    /** 복구 링크 확인 화면용 읽기 전용 조회. 아무것도 잠그지 않는다. */
    User findRecoverableWithdrawalById(@Param("id") Long id,
                                       @Param("currentTime") LocalDateTime currentTime);

    /** 복구 확정 직전의 재검증용 잠금 조회. */
    User findRecoverableWithdrawalByIdForUpdate(@Param("id") Long id,
                                                @Param("currentTime") LocalDateTime currentTime);

    /**
     * 탈퇴 유예 계정을 ACTIVE 로 되돌린다. 상태와 유예 일정만 되돌리고
     * 이메일/닉네임/비밀번호 등 나머지 회원 정보는 건드리지 않는다.
     */
    int restoreWithdrawalPendingAccount(@Param("id") Long id,
                                        @Param("status") UserStatus status,
                                        @Param("currentTime") LocalDateTime currentTime);

    int deactivateAccount(@Param("id") Long id,
                          @Param("userEmail") String userEmail,
                          @Param("nickname") String nickname,
                          @Param("status") UserStatus status);

    /**
     * 유예가 끝난 최종 파기 대상 회원 번호. 한 번에 다 읽지 않고 batch 크기만큼만 가져온다.
     * 관리자 계정은 애초에 탈퇴할 수 없으므로 여기서도 USER 만 본다.
     */
    List<Long> findDueWithdrawalUserIds(@Param("currentTime") LocalDateTime currentTime,
                                        @Param("limit") int limit);

    /**
     * 최종 파기 직전의 잠금 조회. 복구(restoreWithdrawalPendingAccount)와 같은 users 행을 두고
     * 경쟁하므로 여기서 잠근 뒤 조건을 전부 다시 확인한다. 대상이 아니면 null 이다.
     */
    User findPurgeTargetByIdForUpdate(@Param("id") Long id,
                                      @Param("currentTime") LocalDateTime currentTime);

    /**
     * 최종 파기. 개인정보를 지우고 DEACTIVATED 로 바꾼다.
     * 공개 콘텐츠 FK 를 지키기 위해 행 자체는 남기고, 탈퇴 경위(withdrawal_requested_at,
     * purge_scheduled_at)와 created_at, user_role 은 그대로 둔다.
     */
    int finalizeWithdrawal(@Param("id") Long id,
                           @Param("userEmail") String userEmail,
                           @Param("nickname") String nickname,
                           @Param("status") UserStatus status,
                           @Param("deletedAt") LocalDateTime deletedAt,
                           @Param("currentTime") LocalDateTime currentTime);

    /* ---------- 중복 체크 ---------- */
    int countByUsername(String username);
    int countByNickname(String nickname);

    /** 자동 추천용. 같은 조합의 언어별 표기를 한 번에 확인한다. */
    int countByNicknameIn(@Param("nicknames") Collection<String> nicknames);
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

    /** 이메일 없이 남아 있는 예전 Kakao/Naver 회원인지. 요청마다 DB 로 판정한다. */
    boolean isSocialAccountMissingEmail(@Param("id") Long id);

    /**
     * 정상적인 이메일 인증 대기 계정인지. 가입 경로를 가리지 않는다.
     * 이메일 주소 변경 안전장치의 공통 자격 조건이다.
     */
    boolean isEmailVerificationPending(@Param("id") Long id);

    /** 예전 소셜 회원의 이메일 보완이 아직 인증 대기 중인지. 이메일 등록 게이트가 쓴다. */
    boolean isSocialAccountVerificationPending(@Param("id") Long id);

    /**
     * 인증 대기 중인 이메일을 새 이메일로 교체하고 토큰을 새로 발급한다.
     * 지금 이메일이 기대값과 같을 때만 바뀌므로 그사이 인증이 끝났거나 값이 달라졌으면 0행이 된다.
     */
    int changePendingVerificationEmail(
            @Param("id") Long id,
            @Param("userEmail") String userEmail,
            @Param("expectedCurrentEmail") String expectedCurrentEmail,
            @Param("token") String token,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("requestedAt") LocalDateTime requestedAt);

    /**
     * 예전 Kakao/Naver 회원의 이메일 등록 시작. 대상 조건을 WHERE 로 다시 걸어
     * 조건이 하나라도 깨지면 아무것도 바꾸지 않는다.
     */
    int startEmailVerificationForSocialAccount(
            @Param("id") Long id,
            @Param("userEmail") String userEmail,
            @Param("token") String token,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("requestedAt") LocalDateTime requestedAt);

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
