package com.example.travlediary.repository.category;

import com.example.travlediary.model.InfoCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InfoCategoryMapper {

    List<InfoCategory> findAll();

    InfoCategory findById(Long id);

    int insert(InfoCategory category);

    int update(InfoCategory category);

    int countByNameExcludingId(@Param("name") String name,
                               @Param("excludeId") Long excludeId);

    int countTravelInfoByCategoryId(Long categoryId);

    int deleteById(Long id);
}
