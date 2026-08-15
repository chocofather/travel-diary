package com.example.travlediary.repository.user;

import com.example.travlediary.dto.AdminAppealDto;
import com.example.travlediary.model.AppealStatus;
import com.example.travlediary.model.UserAppeal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserAppealMapper {

    int insert(UserAppeal appeal);

    /** 같은 제재에 처리 대기 중인 이의제기. DB UNIQUE 와 함께 중복 접수를 막는다. */
    UserAppeal findPendingBySanctionId(@Param("sanctionId") Long sanctionId);

    /** 제한 안내 화면에 보여줄 최신 이의제기. */
    UserAppeal findLatestBySanctionId(@Param("sanctionId") Long sanctionId);

    /* ---------- 관리자 처리 ---------- */

    long countAdminAppeals(@Param("status") AppealStatus status,
                           @Param("keyword") String keyword);

    List<AdminAppealDto> findAdminAppeals(@Param("status") AppealStatus status,
                                          @Param("keyword") String keyword,
                                          @Param("offset") long offset,
                                          @Param("limit") int limit);

    AdminAppealDto findAdminAppealById(@Param("id") Long id);

    /** 처리 직전 잠금 조회. */
    UserAppeal findByIdForUpdate(@Param("id") Long id);

    /** PENDING 인 이의제기만 승인/기각으로 마감한다. */
    int handle(@Param("id") Long id,
               @Param("status") AppealStatus status,
               @Param("adminId") Long adminId,
               @Param("adminReply") String adminReply,
               @Param("handledAt") LocalDateTime handledAt);
}
