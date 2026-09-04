package com.example.travlediary.repository.info;

import com.example.travlediary.model.RestaurantInfo;
import com.example.travlediary.model.RestaurantInfoTranslation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RestaurantInfoMapper {
    void insert(RestaurantInfo info);

    RestaurantInfo findByDestinationId(Long destinationId);

    /** 한 여행지의 언어별 번역을 한 번에 읽는다. 언어는 서비스가 고른다. */
    List<RestaurantInfoTranslation> findTranslationsByDestinationId(Long destinationId);

    /** 관리자 저장용. 언어 한 줄씩 넣고/고치고/지운다. */
    void insertTranslation(RestaurantInfoTranslation translation);

    void updateTranslation(RestaurantInfoTranslation translation);

    void deleteTranslation(@Param("destinationId") Long destinationId,
                           @Param("languageCode") String languageCode);

    void update(RestaurantInfo info);

}
