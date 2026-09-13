package com.example.travlediary.repository.user;

import com.example.travlediary.model.SanctionReleaseVia;
import com.example.travlediary.model.SanctionStatus;
import com.example.travlediary.model.UserSanction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserSanctionMapper {

    /** 새 제재 저장. 회원당 ACTIVE 1건은 DB UNIQUE 로도 보장된다. */
    int insert(UserSanction sanction);

    UserSanction findActiveByUserId(@Param("userId") Long userId);

    UserSanction findActiveByUserIdForUpdate(@Param("userId") Long userId);

    List<UserSanction> findByUserId(@Param("userId") Long userId);

    /** 기간이 지난 적용중 제재. 자동 만료 배치가 사용한다. */
    List<UserSanction> findExpiredActiveSanctions(@Param("now") LocalDateTime now);

    /** ACTIVE 제재를 EXPIRED 또는 LIFTED 로 종료한다. */
    int release(@Param("id") Long id,
                @Param("status") SanctionStatus status,
                @Param("releasedAt") LocalDateTime releasedAt,
                @Param("releasedBy") Long releasedBy,
                @Param("releasedVia") SanctionReleaseVia releasedVia,
                @Param("releaseReason") String releaseReason);

    /**
     * 보관기간이 끝난 제재의 id. 기준일이 두 갈래다.
     * 종료된 제재(EXPIRED/LIFTED)는 실제 종료 시각(released_at) 기준이고,
     * 적용중인 영구제재는 회원이 최종 탈퇴한 경우에만 users.deleted_at 기준으로 센다.
     * 계정이 살아 있는 회원의 적용중 제재는 기간과 무관하게 대상이 아니다.
     *
     * @param retentionCutoff 이 시각 이전에 종료(또는 최종 탈퇴)된 제재만 대상
     * @param limit 한 번에 가져올 제재 수
     */
    List<Long> findRetentionExpiredSanctionIds(
            @Param("retentionCutoff") LocalDateTime retentionCutoff,
            @Param("limit") int limit);

    /**
     * 보관기간이 끝난 제재 한 건 삭제. 목록을 읽은 뒤 제재가 다시 걸렸거나 회원이 복구했을 수
     * 있으므로 두 갈래 보관조건을 그대로 다시 확인한다.
     *
     * @return 지웠으면 1, 더 이상 대상이 아니면 0
     */
    int deleteRetentionExpiredSanction(
            @Param("id") Long id,
            @Param("retentionCutoff") LocalDateTime retentionCutoff);
}