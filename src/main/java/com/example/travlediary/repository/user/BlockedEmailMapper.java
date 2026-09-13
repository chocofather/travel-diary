package com.example.travlediary.repository.user;

import com.example.travlediary.model.BlockedEmail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BlockedEmailMapper {

    /** 영구제한 회원의 재가입 차단 기록. 원본 이메일이 아니라 해시만 저장한다. */
    int insert(BlockedEmail blockedEmail);

    /** 제재 해제 시 해당 제재로 만들어진 차단을 해제한다. */
    int releaseBySanctionId(@Param("sanctionId") Long sanctionId,
                            @Param("releasedAt") LocalDateTime releasedAt,
                            @Param("releasedBy") Long releasedBy);

    int countActiveByEmailHash(@Param("emailHash") String emailHash);

    /**
     * 재가입 차단 기록의 보관기간이 끝난 회원의 id.
     * 이 기록의 목적이 "탈퇴 후 재가입 차단"이라 기준은 최종 탈퇴 완료 시각(users.deleted_at)이다.
     * 제재 종료 시각이 아니다.
     *
     * @param retentionCutoff 이 시각 이전에 최종 탈퇴가 끝난 회원만 대상
     * @param limit 한 번에 가져올 회원 수
     */
    List<Long> findReSignupBlockRetentionExpiredUserIds(
            @Param("retentionCutoff") LocalDateTime retentionCutoff,
            @Param("limit") int limit);

    /**
     * 보관기간이 끝난 탈퇴 회원의 재가입 차단 기록 삭제.
     * 목록을 읽은 뒤 조건이 바뀌었을 수 있으므로 보관조건을 다시 확인한다.
     *
     * @return 지운 행 수
     */
    int deleteReSignupBlocksOfRetentionExpiredUser(
            @Param("userId") Long userId,
            @Param("retentionCutoff") LocalDateTime retentionCutoff);
}