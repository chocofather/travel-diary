package com.example.travlediary.repository.category;

import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.InfoCategoryTranslation;
import com.example.travlediary.model.TravelInfoContentType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InfoCategoryMapper {

    List<InfoCategory> findAll();

    List<InfoCategory> findVisible();

    List<InfoCategory> findVisibleByContentType(@Param("contentType") TravelInfoContentType contentType);

    InfoCategory findById(Long id);

    int insert(InfoCategory category);

    int update(InfoCategory category);

    int countByNameExcludingId(@Param("name") String name,
                               @Param("excludeId") Long excludeId);

    int countTravelInfoByCategoryId(Long categoryId);

    int deleteById(Long id);

    /** 관리자 수정 화면용. 카테고리 한 건의 번역을 언어 코드 순으로 읽는다. */
    List<InfoCategoryTranslation> findTranslationsByCategoryId(Long infoCategoryId);

    /** 목록·필터용. 여러 카테고리의 번역을 한 번에 읽어 언어 대체에서 N+1 이 생기지 않게 한다. */
    List<InfoCategoryTranslation> findTranslationsByCategoryIds(
            @Param("infoCategoryIds") List<Long> infoCategoryIds);

    /** 관리자 저장용. 언어 한 줄이 단위이며 UNIQUE(info_category_id, language_code) 를 따른다. */
    int insertTranslation(InfoCategoryTranslation translation);

    int updateTranslation(InfoCategoryTranslation translation);

    int deleteTranslation(@Param("infoCategoryId") Long infoCategoryId,
                          @Param("languageCode") String languageCode);
}
