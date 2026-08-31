package com.example.travlediary.repository.travelinfo;

import com.example.travlediary.model.FestivalInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FestivalInfoMapper {

    FestivalInfo findByInfoId(Long infoId);

    int countBySourceTypeAndExternalContentId(@Param("sourceType") String sourceType,
                                              @Param("externalContentId") String externalContentId);

    int insert(FestivalInfo festivalInfo);

    int update(FestivalInfo festivalInfo);

    int deleteByInfoId(Long infoId);
}
