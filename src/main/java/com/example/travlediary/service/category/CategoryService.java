package com.example.travlediary.service.category;

import com.example.travlediary.dto.CategoryForm;
import com.example.travlediary.model.Category;
import com.example.travlediary.model.CategoryDestinationType;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.repository.category.CategoryMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public List<Category> getAll() {
        return categoryMapper.findAll();
    }

    /**
     * 카테고리별 "적용 가능한 여행지 유형" 태그 (예: 1 -> "ATTRACTION SHOP").
     * 화면 필터가 이름이 아니라 이 매핑만 보고 동작하도록 서버에서 만들어 준다.
     * 매핑이 없는 카테고리는 키 자체가 없으며 [전체] 에서만 보인다.
     */
    public Map<Long, String> getCategoryDestinationTypeTags() {
        List<CategoryDestinationType> mappings = categoryMapper.findCategoryDestinationTypes();
        if (mappings == null || mappings.isEmpty()) {
            return Map.of();
        }

        Map<Long, StringBuilder> tags = new LinkedHashMap<>();
        for (CategoryDestinationType mapping : mappings) {
            if (mapping == null || mapping.getCategoryId() == null
                    || mapping.getDestinationType() == null || mapping.getDestinationType().isBlank()) {
                continue;
            }
            StringBuilder tag = tags.computeIfAbsent(mapping.getCategoryId(), id -> new StringBuilder());
            if (!tag.isEmpty()) {
                tag.append(' ');
            }
            tag.append(mapping.getDestinationType());
        }

        Map<Long, String> result = new LinkedHashMap<>();
        tags.forEach((categoryId, tag) -> result.put(categoryId, tag.toString()));
        return result;
    }

    /** 해당 여행지 유형에 적용 가능한 카테고리만 (category_destination_types 매핑 기준). */
    public List<Category> getByDestinationType(DestinationType type) {
        List<Category> categories = categoryMapper.findByDestinationType(type.name());
        return categories == null ? List.of() : categories;
    }

    /**
     * 여러 유형을 한 화면에서 함께 쓰는 경우(음식점/카페처럼)의 합집합.
     * 같은 카테고리가 여러 유형에 매핑돼 있어도 한 번만 담고, id 순서를 유지한다.
     */
    public List<Category> getByDestinationTypes(DestinationType... types) {
        Map<Long, Category> merged = new TreeMap<>();
        for (DestinationType type : types) {
            for (Category category : getByDestinationType(type)) {
                if (category != null && category.getId() != null) {
                    merged.putIfAbsent(category.getId(), category);
                }
            }
        }
        return List.copyOf(merged.values());
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
