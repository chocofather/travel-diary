package com.example.travlediary.repository.translation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.time.LocalDate;

@Mapper
public interface GoogleTranslationDailyUsageMapper {
    void insertUserDayIfAbsent(@Param("usageDate") LocalDate usageDate,
                               @Param("userId") Long userId,
                               @Param("now") Timestamp now);

    int reserveUserIfWithinLimit(@Param("usageDate") LocalDate usageDate,
                                 @Param("userId") Long userId,
                                 @Param("characters") long characters,
                                 @Param("characterLimit") long characterLimit,
                                 @Param("now") Timestamp now);

    void insertIpDayIfAbsent(@Param("usageDate") LocalDate usageDate,
                             @Param("ipHash") byte[] ipHash,
                             @Param("now") Timestamp now);

    int reserveIpIfWithinLimit(@Param("usageDate") LocalDate usageDate,
                               @Param("ipHash") byte[] ipHash,
                               @Param("characters") long characters,
                               @Param("characterLimit") long characterLimit,
                               @Param("now") Timestamp now);

    /** 최종 탈퇴 파기용. 회원에 묶인 사용량 집계를 지운다(IP 집계는 건드리지 않는다). */
    int deleteAllByUserId(@Param("userId") Long userId);
}
