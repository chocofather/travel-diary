package com.example.travlediary.repository.user;

import com.example.travlediary.model.BlockedEmail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface BlockedEmailMapper {

    /** 영구제한 회원의 재가입 차단 기록. 원본 이메일이 아니라 해시만 저장한다. */
    int insert(BlockedEmail blockedEmail);

    /** 제재 해제 시 해당 제재로 만들어진 차단을 해제한다. */
    int releaseBySanctionId(@Param("sanctionId") Long sanctionId,
                            @Param("releasedAt") LocalDateTime releasedAt,
                            @Param("releasedBy") Long releasedBy);

    int countActiveByEmailHash(@Param("emailHash") String emailHash);
}