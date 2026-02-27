package com.example.travlediary.service.category;

import com.example.travlediary.dto.CategoryForm;
import com.example.travlediary.model.Category;
import com.example.travlediary.repository.category.CategoryMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public List<Category> getAll() {
        return categoryMapper.findAll();
    }

    public void createCategory(CategoryForm form) {
        Category category = new Category();
        category.setName(form.getName());
        categoryMapper.insert(category);
    }

    public String getFirstCategoryNameByDestinationId(Long destinationId) {
        List<String> categories = categoryMapper.getCategoryNamesByDestinationId(destinationId);
        return categories.isEmpty() ? null : categories.get(0);
    }

    public void deleteCategory(Long id) {
        categoryMapper.deleteById(id);
    }
}
