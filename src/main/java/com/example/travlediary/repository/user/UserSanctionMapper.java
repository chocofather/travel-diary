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
}