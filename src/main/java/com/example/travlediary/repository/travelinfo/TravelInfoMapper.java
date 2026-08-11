package com.example.travlediary.repository.travelinfo;

import com.example.travlediary.dto.AdminTravelInfoListItemDto;
import com.example.travlediary.dto.TravelInfoDetailDto;
import com.example.travlediary.dto.TravelInfoListItemDto;
import com.example.travlediary.model.InfoPeriod;
import com.example.travlediary.model.InfoImage;
import com.example.travlediary.model.TravelInfo;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
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

    List<String> findMainImageUrlsByInfoId(Long infoId);

    int insertInfoImage(InfoImage infoImage);

    int deleteMainImagesByInfoId(Long infoId);
}
