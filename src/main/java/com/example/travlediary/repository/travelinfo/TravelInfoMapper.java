package com.example.travlediary.repository.travelinfo;

import com.example.travlediary.dto.AdminTravelInfoListItemDto;
import com.example.travlediary.model.InfoPeriod;
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

    TravelInfo findById(Long id);

    TravelInfo findByIdForUpdate(Long id);

    int insertTravelInfo(TravelInfo travelInfo);

    int updateTravelInfo(TravelInfo travelInfo);

    int deleteTravelInfo(Long id);

    List<InfoPeriod> findPeriodsByInfoId(Long infoId);

    int insertPeriod(InfoPeriod period);

    int deletePeriodsByInfoId(Long infoId);
}
