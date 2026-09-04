package com.example.travlediary.repository.info;


import com.example.travlediary.model.ActivityInfo;
import com.example.travlediary.model.ActivityInfoTranslation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ActivityInfoMapper {

    void insert(ActivityInfo info);

    ActivityInfo findByDestinationId(Long destinationId);

    /** 한 여행지의 언어별 번역을 한 번에 읽는다. 언어는 서비스가 고른다. */
    List<ActivityInfoTranslation> findTranslationsByDestinationId(Long destinationId);

    /** 관리자 저장용. 언어 한 줄씩 넣고/고치고/지운다. */
    void insertTranslation(ActivityInfoTranslation translation);

    void updateTranslation(ActivityInfoTranslation translation);

    void deleteTranslation(@Param("destinationId") Long destinationId,
                           @Param("languageCode") String languageCode);

    void update(ActivityInfo info);

}
