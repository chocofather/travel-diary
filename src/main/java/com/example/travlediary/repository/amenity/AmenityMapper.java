package com.example.travlediary.repository.amenity;

import com.example.travlediary.dto.AmenityDto;
import com.example.travlediary.model.Amenity;
import com.example.travlediary.model.AmenityDestinationType;
import com.example.travlediary.model.AmenityTranslation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AmenityMapper {
    List<Amenity> findAll();

    Amenity selectAmenityById(@Param("id") Integer id); // 단일 amenity 조회
    int insertAmenity(Amenity amenity); // amenity 등록

    /**
     * 관리자 목록 한 줄(아이콘 / ko 이름 / code).
     * ko 번역이 없는 편의시설도 빠지지 않도록 LEFT JOIN 으로 읽는다.
     */
    List<AmenityDto> findAdminAmenityRows();

    /** amenities.code 는 UNIQUE 이므로 등록 전에 중복을 먼저 확인한다. */
    int countByCode(@Param("code") String code);

    /** 아이콘 파일 저장 성공 후 실제 웹 경로를 기록한다. code 는 바꾸지 않는다. */
    int updateAmenityIconUrl(@Param("id") Integer id, @Param("iconUrl") String iconUrl);

    // 타입별로 필요한 편의시설 목록 (ex: attraction/숙소/음식점 등등)
    List<Amenity> findByType(@Param("type") String type);

    // 다국어 지원
    List<AmenityTranslation> findTranslationsByAmenityId(@Param("amenityId") int amenityId);
    List<AmenityTranslation> findTranslationsByLang(@Param("languageCode") String languageCode);
    AmenityTranslation findTranslation(@Param("amenityId") Integer amenityId, @Param("languageCode") String languageCode); // 단건 번역 조회


    int insertAmenityTranslation(AmenityTranslation translation); // 번역 등록

    /** 기존 번역 이름 변경. UNIQUE(amenity_id, language_code) 기준 단건. */
    int updateAmenityTranslation(AmenityTranslation translation);

    /** 선택 언어 값을 비운 경우 해당 번역 행을 지운다. */
    int deleteAmenityTranslation(@Param("amenityId") Integer amenityId,
                                 @Param("languageCode") String languageCode);

    List<AmenityTranslation> findTranslationsByTypeAndLang(
            @Param("type") String type,
            @Param("languageCode") String languageCode
    );

    /**
     * 여행지 유형별 편의시설 마스터 목록.
     * 사용 이력(*_amenities)이 아니라 amenity_destination_types 매핑을 읽는다.
     *
     * @param destinationType DestinationType enum 이름
     */
    List<AmenityTranslation> findTranslationsByDestinationTypeAndLang(
            @Param("destinationType") String destinationType,
            @Param("languageCode") String languageCode
    );

    /** 편의시설 ↔ 여행지 유형 마스터 매핑 전체. 화면 필터가 쓰는 태그의 원본이다. */
    List<AmenityDestinationType> findAmenityDestinationTypes();

    /** 편의시설 1건의 적용 가능한 여행지 유형. 수정 화면 체크 상태 복원용. */
    List<AmenityDestinationType> findAmenityDestinationTypesByAmenityId(
            @Param("amenityId") Integer amenityId);

    /**
     * 편의시설 ↔ 여행지 유형 매핑 1건 등록.
     *
     * @param destinationType DestinationType enum 이름을 그대로 저장한다.
     */
    int insertAmenityDestinationType(@Param("amenityId") Integer amenityId,
                                     @Param("destinationType") String destinationType);

    /** 편의시설 1건의 매핑 전체 삭제. 수정 시 삭제 후 선택값 재삽입에 쓴다. */
    int deleteAmenityDestinationTypesByAmenityId(@Param("amenityId") Integer amenityId);



    // ========= 편의시설 연결 INSERT =========
    int insertAttractionAmenity(@Param("attractionId") Long attractionId, @Param("amenityId") Integer amenityId);
    int insertAccommodationAmenity(@Param("accommodationId") Long accommodationId, @Param("amenityId") Integer amenityId);
    int insertRestaurantAmenity(@Param("restaurantId") Long restaurantId, @Param("amenityId") Integer amenityId);
    int insertActivityAmenity(@Param("activityId") Long activityId, @Param("amenityId") Integer amenityId);
    int insertShopAmenity(@Param("shopId") Long shopId, @Param("amenityId") Integer amenityId);

    // ========== 상세페이지용: destinationId 기준으로 번역 amenity 리스트 반환 ==========
    List<AmenityTranslation> findAttractionAmenityTranslationsByDestinationId(
            @Param("destinationId") Long destinationId,
            @Param("lang") String lang
    );
    List<AmenityTranslation> findAccommodationAmenityTranslationsByDestinationId(
            @Param("destinationId") Long destinationId,
            @Param("lang") String lang
    );
    List<AmenityTranslation> findRestaurantAmenityTranslationsByDestinationId(
            @Param("destinationId") Long destinationId,
            @Param("lang") String lang
    );
    List<AmenityTranslation> findActivityAmenityTranslationsByDestinationId(
            @Param("destinationId") Long destinationId,
            @Param("lang") String lang
    );
    List<AmenityTranslation> findShopAmenityTranslationsByDestinationId(
            @Param("destinationId") Long destinationId,
            @Param("lang") String lang
    );

    void deleteAttractionAmenities(Long attractionId);
    void deleteAccommodationAmenities(Long accommodationId);
    void deleteRestaurantAmenities(Long restaurantId);
    void deleteActivityAmenities(Long activityId);
    void deleteShopAmenities(Long shopId);




}
