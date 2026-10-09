package com.tripbora.repository.category;

import com.tripbora.model.CountryCategory;
import com.tripbora.model.CountryCategoryTranslation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CountryCategoryMapper {
    void insert(CountryCategory category);

    // 전체삭제
    void deleteAll();

    // 새로운 데이터 추가
    List<Integer> selectAllIds();

    List<CountryCategory> selectCountries(); // 국가만 조회

    List<CountryCategory> selectCourseCountries();

/*
    List<CountryCategory> selectByParentId(Integer parentId);
*/

    List<CountryCategory> selectDepth1();

    List<CountryCategory> findByDepth(@Param("depth") int depth, @Param("parentId") Long parentId);

    CountryCategory selectById(Long id);

    void updateIconPath(@Param("id") Long id,
                        @Param("iconPath") String iconPath);

    List<CountryCategory> selectByParentId(Long parentId);

    List<Long> findAllRegionIdsUnder(@Param("rootRegionId") Long rootRegionId);

    List<CountryCategory> selectByParentIdAndDepth(@Param("parentId") Long parentId, @Param("depth") int depth);

    // 대한민국 추출
    CountryCategory selectByRegionNameAndDepth(@Param("name") String name, @Param("depth") int depth);

    String getCodeById(@Param("id") Long id);

    List<CountryCategory> selectByIds(@org.apache.ibatis.annotations.Param("ids") List<Long> ids);

    List<CountryCategoryTranslation> findTranslationsByCountryCategoryIds(
            @Param("countryCategoryIds") Collection<Long> countryCategoryIds);

    List<CountryCategory> findRandomOverseasCountries(@Param("limit") int limit);

    // ─── 관리자 지역 등록/삭제 ───

    /** id 는 DB 가 정한다. 시드 적재용 {@link #insert} 와 달리 생성된 id 를 돌려받는다. */
    int insertRegion(CountryCategory category);

    int insertTranslation(CountryCategoryTranslation translation);

    /** 지역 이름만 바꾼다. id·parent_id·depth·code 는 그대로 둔다. */
    int updateRegionNames(@Param("id") Long id, @Param("regionName") String regionName,
                          @Param("nameEn") String nameEn);

    /** (지역, 언어) 줄이 있으면 이름만 바꾸고 없으면 넣는다. */
    int upsertTranslation(CountryCategoryTranslation translation);

    int deleteTranslation(@Param("countryCategoryId") Long countryCategoryId,
                          @Param("languageCode") String languageCode);

    /** 번역·지역 태그 매핑은 FK ON DELETE CASCADE 로 함께 지워진다. */
    int deleteById(@Param("id") Long id);

    int countChildren(@Param("id") Long id);

    int countDestinationsByRegionId(@Param("id") Long id);

    int countCoursesByCountryId(@Param("id") Long id);

    int countByCode(@Param("code") String code);

    int countOtherRegionsByIconPath(@Param("id") Long id, @Param("iconPath") String iconPath);

    /** 부모 바로 아래 지역들이 쓰는 depth 값(중복 제거). */
    List<Integer> selectChildDepths(@Param("parentId") Long parentId);

    /** 조부모 아래 손자 지역들이 쓰는 depth 값(중복 제거). 형제가 없을 때 같은 계층의 depth 를 찾는다. */
    List<Integer> selectGrandchildDepths(@Param("grandParentId") Long grandParentId);

}
