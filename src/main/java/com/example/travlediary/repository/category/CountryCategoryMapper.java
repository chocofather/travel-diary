package com.example.travlediary.repository.category;

import com.example.travlediary.model.CountryCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    List<CountryCategory> selectByParentIdAndDepth(@Param("parentId") Long parentId, @Param("depth") int depth);

    // 대한민국 추출
    CountryCategory selectByRegionNameAndDepth(@Param("name") String name, @Param("depth") int depth);

    String getCodeById(@Param("id") Long id);

    List<CountryCategory> selectByIds(@org.apache.ibatis.annotations.Param("ids") List<Long> ids);

    List<CountryCategory> findRandomOverseasCountries(@Param("limit") int limit);

}
