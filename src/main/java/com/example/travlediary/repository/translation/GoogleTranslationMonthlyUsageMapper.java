package com.example.travlediary.repository.translation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;

@Mapper
public interface GoogleTranslationMonthlyUsageMapper {
    void insertMonthIfAbsent(@Param("monthKey") String monthKey,
                            @Param("now") Timestamp now);

    int reserveIfWithinLimit(@Param("monthKey") String monthKey,
                             @Param("characters") long characters,
                             @Param("characterLimit") long characterLimit,
                             @Param("now") Timestamp now);
}
