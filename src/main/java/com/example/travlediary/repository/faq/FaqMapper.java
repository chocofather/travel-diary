package com.example.travlediary.repository.faq;

import com.example.travlediary.dto.FaqCategoryListItemDto;
import com.example.travlediary.dto.FaqListItemDto;
import com.example.travlediary.model.Faq;
import com.example.travlediary.model.FaqCategory;
import com.example.travlediary.model.FaqCategoryTranslation;
import com.example.travlediary.model.FaqTranslation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FaqMapper {

    List<FaqListItemDto> findAdminList();

    List<FaqListItemDto> findPublicList();

    List<FaqCategory> findCategories();

    FaqCategory findCategoryById(@Param("id") Long id);

    Faq findById(@Param("id") Long id);

    Faq findByIdForUpdate(@Param("id") Long id);

    int insertFaq(Faq faq);

    int updateFaq(Faq faq);

    int deleteFaq(@Param("id") Long id);

    /** 관리자 수정 화면용. 질문 한 건의 번역을 언어 코드 순으로 읽는다. */
    List<FaqTranslation> findTranslationsByFaqId(@Param("faqId") Long faqId);

    /** 공개 목록용. 여러 질문의 번역을 한 번에 읽어 언어 대체에서 N+1 이 생기지 않게 한다. */
    List<FaqTranslation> findTranslationsByFaqIds(@Param("faqIds") List<Long> faqIds);

    /** 관리자 저장용. 언어 한 줄이 단위이며 UNIQUE(faq_id, language_code) 를 따른다. */
    int insertTranslation(FaqTranslation translation);

    int updateTranslation(FaqTranslation translation);

    int deleteTranslation(@Param("faqId") Long faqId,
                          @Param("languageCode") String languageCode);

    /** 관리자 카테고리 목록. 사용 중인 FAQ 수를 함께 읽어 한 번의 조회로 끝낸다. */
    List<FaqCategoryListItemDto> findAdminCategories();

    int insertCategory(FaqCategory category);

    int updateCategory(FaqCategory category);

    int deleteCategory(@Param("id") Long id);

    /** category_name 은 UNIQUE 다. 수정 시 자기 자신은 제외하고 센다. */
    int countCategoriesByNameExcludingId(@Param("categoryName") String categoryName,
                                         @Param("excludeId") Long excludeId);

    /** 이 카테고리를 쓰는 FAQ 수. 삭제 전 차단 기준이다. */
    int countFaqsByCategoryId(@Param("categoryId") Long categoryId);

    /** 관리자 수정 화면용. 카테고리 한 건의 이름 번역을 언어 코드 순으로 읽는다. */
    List<FaqCategoryTranslation> findCategoryTranslationsByCategoryId(
            @Param("faqCategoryId") Long faqCategoryId);

    /** 공개 목록용. 여러 카테고리의 이름 번역을 한 번에 읽어 N+1 이 생기지 않게 한다. */
    List<FaqCategoryTranslation> findCategoryTranslationsByCategoryIds(
            @Param("faqCategoryIds") List<Long> faqCategoryIds);

    /** 관리자 저장용. 언어 한 줄이 단위이며 UNIQUE(faq_category_id, language_code) 를 따른다. */
    int insertCategoryTranslation(FaqCategoryTranslation translation);

    int updateCategoryTranslation(FaqCategoryTranslation translation);

    int deleteCategoryTranslation(@Param("faqCategoryId") Long faqCategoryId,
                                  @Param("languageCode") String languageCode);
}
