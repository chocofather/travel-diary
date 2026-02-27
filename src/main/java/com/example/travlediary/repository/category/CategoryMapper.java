package com.example.travlediary.repository.category;

import com.example.travlediary.model.Category;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CategoryMapper {
    List<Category> findAll();

    void insert(Category category);

    void deleteById(Long id);

    List<String> getCategoryNamesByDestinationId(Long destinationId);
}
