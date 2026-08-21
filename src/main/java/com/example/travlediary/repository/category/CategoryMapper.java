package com.example.travlediary.repository.category;

import com.example.travlediary.model.Category;
import com.example.travlediary.model.CategoryDestinationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    void insert(Category category);

    void deleteById(Long id);

    List<String> getCategoryNamesByDestinationId(Long destinationId);
}
