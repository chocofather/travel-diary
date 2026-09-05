package com.example.travlediary.repository.travelinfo;

import com.example.travlediary.dto.AdminTravelInfoListItemDto;
import com.example.travlediary.dto.TravelInfoDetailDto;
import com.example.travlediary.dto.TravelInfoListItemDto;
import com.example.travlediary.model.InfoPeriod;
import com.example.travlediary.model.InfoImage;
import com.example.travlediary.model.TravelInfo;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.model.TravelInfoTranslation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TravelInfoMapper {

    List<AdminTravelInfoListItemDto> findAdminList(
            @Param("scope") TravelInfoScope scope,
            @Param("contentType") TravelInfoContentType contentType,
            @Param("categoryId") Long categoryId);

    List<TravelInfoListItemDto> findPublicList(
            @Param("scope") TravelInfoScope scope,
            @Param("contentType") TravelInfoContentType contentType,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("keywordPattern") String keywordPattern,
            @Param("koreanPattern") String koreanPattern,
            @Param("sort") String sort,
            @Param("offset") long offset,
            @Param("limit") int limit);

    long countPublicList(
            @Param("scope") TravelInfoScope scope,
            @Param("contentType") TravelInfoContentType contentType,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("keywordPattern") String keywordPattern,
            @Param("koreanPattern") String koreanPattern);

    int incrementPublicViews(@Param("id") Long id);

    TravelInfoDetailDto findPublicDetailById(@Param("id") Long id);

    Long findPublicBookmarkTargetForUpdate(@Param("id") Long id);

    TravelInfo findById(Long id);

    TravelInfo findByIdForUpdate(Long id);

    int insertTravelInfo(TravelInfo travelInfo);

    int updateTravelInfo(TravelInfo travelInfo);

    int deleteTravelInfo(Long id);

    List<InfoPeriod> findPeriodsByInfoId(Long infoId);

    int insertPeriod(InfoPeriod period);

    int deletePeriodsByInfoId(Long infoId);

    InfoImage findMainImageByInfoId(Long infoId);

    List<InfoImage> findImagesByInfoId(Long infoId);

    List<String> findMainImageUrlsByInfoId(Long infoId);

    int insertInfoImage(InfoImage infoImage);

    int clearThumbnailsByInfoId(Long infoId);

    int setThumbnailByIdAndInfoId(@Param("imageId") Long imageId,
                                  @Param("infoId") Long infoId);

    int deleteMainImagesByInfoId(Long infoId);

    /** 상세 화면용. 여행정보 한 건의 번역을 언어 코드 순으로 읽는다. */
    List<TravelInfoTranslation> findTranslationsByInfoId(Long infoId);

    /** 목록 화면용. 여러 여행정보의 번역을 한 번에 읽어 언어 대체에서 N+1 이 생기지 않게 한다. */
    List<TravelInfoTranslation> findTranslationsByInfoIds(@Param("infoIds") List<Long> infoIds);

    /** 관리자 저장용. 언어 한 줄이 단위이며 UNIQUE(travel_info_id, language_code) 를 따른다. */
    int insertTranslation(TravelInfoTranslation translation);

    int updateTranslation(TravelInfoTranslation translation);

    int deleteTranslation(@Param("travelInfoId") Long travelInfoId,
                          @Param("languageCode") String languageCode);
}
