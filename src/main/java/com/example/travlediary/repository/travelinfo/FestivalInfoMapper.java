package com.example.travlediary.repository.travelinfo;

import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.model.FestivalInfoTranslation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FestivalInfoMapper {

    FestivalInfo findByInfoId(Long infoId);

    int countBySourceTypeAndExternalContentId(@Param("sourceType") String sourceType,
                                              @Param("externalContentId") String externalContentId);

    int insert(FestivalInfo festivalInfo);

    int update(FestivalInfo festivalInfo);

    int deleteByInfoId(Long infoId);

    /** 상세·관리자 화면용. 축제 한 건의 번역을 언어 코드 순으로 읽는다. */
    List<FestivalInfoTranslation> findTranslationsByInfoId(Long infoId);

    /** 여러 축제를 한 번에 볼 때 쓴다. 축제 수만큼 조회가 늘지 않게 한 번으로 묶는다. */
    List<FestivalInfoTranslation> findTranslationsByInfoIds(@Param("infoIds") List<Long> infoIds);

    /** 관리자 저장용. 언어 한 줄이 단위이며 UNIQUE(info_id, language_code) 를 따른다. */
    int insertTranslation(FestivalInfoTranslation translation);

    int updateTranslation(FestivalInfoTranslation translation);

    int deleteTranslation(@Param("infoId") Long infoId,
                          @Param("languageCode") String languageCode);
}
