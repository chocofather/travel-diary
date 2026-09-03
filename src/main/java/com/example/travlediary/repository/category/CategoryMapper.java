package com.example.travlediary.repository.category;

import com.example.travlediary.model.Category;
import com.example.travlediary.model.CategoryDestinationType;
import com.example.travlediary.model.CategoryTranslation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CategoryMapper {
    List<Category> findAll();

    /**
     * 여행지 유형별 카테고리 마스터 목록.
     * 사용 이력(destination_categories)이 아니라 category_destination_types 매핑을 읽는다.
     *
     * @param destinationType DestinationType enum 이름
     */
    List<Category> findByDestinationType(@Param("destinationType") String destinationType);

    /** 카테고리 ↔ 여행지 유형 마스터 매핑 전체. 화면 필터가 쓰는 태그의 원본이다. */
    List<CategoryDestinationType> findCategoryDestinationTypes();

    /** 카테고리 1건의 적용 가능한 여행지 유형. 수정 화면 체크 상태 복원용. */
    List<CategoryDestinationType> findCategoryDestinationTypesByCategoryId(
            @Param("categoryId") Long categoryId);

    /**
     * 카테고리 ↔ 여행지 유형 매핑 1건 등록.
     *
     * @param destinationType DestinationType enum 이름을 그대로 저장한다.
     */
    int insertCategoryDestinationType(@Param("categoryId") Long categoryId,
                                      @Param("destinationType") String destinationType);

    /** 카테고리 1건의 매핑 전체 삭제. 수정 시 삭제 후 선택값 재삽입에 쓴다. */
    int deleteCategoryDestinationTypesByCategoryId(@Param("categoryId") Long categoryId);

    Category findById(Long id);

    List<Category> findByIds(@Param("categoryIds") Collection<Long> categoryIds);

    List<CategoryTranslation> findTranslationsByCategoryIds(
            @Param("categoryIds") Collection<Long> categoryIds);

    void insert(Category category);

    /** 카테고리 이름 변경. id 와 매핑은 건드리지 않는다. */
    int update(Category category);

    /** categories.name 은 UNIQUE 다. 수정 시 자기 자신은 제외하고 센다. */
    int countByNameExcludingId(@Param("name") String name, @Param("excludeId") Long excludeId);

    /**
     * 이 카테고리를 실제로 사용하는 여행지 수.
     * categories 삭제는 destination_categories 까지 CASCADE 되므로 삭제 전에 반드시 확인한다.
     */
    int countDestinationsByCategoryId(Long categoryId);

    void deleteById(Long id);

    List<String> getCategoryNamesByDestinationId(Long destinationId);
}
