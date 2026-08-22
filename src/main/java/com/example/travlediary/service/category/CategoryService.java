package com.example.travlediary.service.category;

import com.example.travlediary.dto.CategoryForm;
import com.example.travlediary.model.Category;
import com.example.travlediary.model.CategoryDestinationType;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.repository.category.CategoryMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class CategoryService {

    /** categories.name 은 varchar(100) */
    private static final int MAX_CATEGORY_NAME_LENGTH = 100;

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

    /**
     * 관리자 카테고리 통합 등록.
     * categories 와 category_destination_types 를 한 트랜잭션에서 저장한다.
     * 저장된 매핑은 여행지 등록/수정 폼의 유형 필터가 그대로 읽어 간다.
     */
    @Transactional
    public void createCategory(CategoryForm form) {
        if (form == null) {
            throw new CategoryValidationException("name", "카테고리 정보를 입력해 주세요.");
        }

        String name = requiredName(form.getName());
        List<DestinationType> types = requiredDestinationTypes(form.getDestinationTypes());
        ensureUniqueName(name, null);

        Category category = new Category();
        category.setName(name);
        try {
            // useGeneratedKeys 로 INSERT 직후 category.getId() 가 채워진다.
            categoryMapper.insert(category);
        } catch (DuplicateKeyException exception) {
            // 사전 검사와 INSERT 사이의 경합은 UNIQUE 제약이 잡아 준다.
            throw new DuplicateCategoryNameException(exception);
        }

        for (DestinationType type : types) {
            categoryMapper.insertCategoryDestinationType(category.getId(), type.name());
        }
    }

    /** 수정 화면 복원용. 이름과 적용 대상 체크 상태를 폼에 담아 돌려준다. */
    @Transactional(readOnly = true)
    public CategoryForm getCategoryForm(Long id) {
        Category category = requireCategory(id);

        CategoryForm form = new CategoryForm();
        form.setId(category.getId());
        form.setName(category.getName());

        List<DestinationType> types = new ArrayList<>();
        List<CategoryDestinationType> mappings =
                categoryMapper.findCategoryDestinationTypesByCategoryId(id);
        if (mappings != null) {
            for (CategoryDestinationType mapping : mappings) {
                if (mapping != null && mapping.getDestinationType() != null) {
                    types.add(DestinationType.valueOf(mapping.getDestinationType()));
                }
            }
        }
        form.setDestinationTypes(types);
        return form;
    }

    /**
     * 관리자 카테고리 수정.
     * 이름과 적용 대상을 한 트랜잭션에서 갱신한다.
     * 매핑은 복합 PK 뿐이라 잃을 정보가 없어 전체 삭제 후 선택값을 다시 넣는다.
     */
    @Transactional
    public void updateCategory(CategoryForm form) {
        if (form == null || form.getId() == null) {
            throw new CategoryValidationException("name", "수정할 카테고리를 찾을 수 없습니다.");
        }

        Category category = requireCategory(form.getId());
        Long categoryId = category.getId();

        String name = requiredName(form.getName());
        List<DestinationType> types = requiredDestinationTypes(form.getDestinationTypes());
        // 자기 자신은 중복 대상에서 제외한다 (이름을 그대로 두고 저장하는 경우)
        ensureUniqueName(name, categoryId);

        category.setName(name);
        try {
            categoryMapper.update(category);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateCategoryNameException(exception);
        }

        categoryMapper.deleteCategoryDestinationTypesByCategoryId(categoryId);
        for (DestinationType type : types) {
            categoryMapper.insertCategoryDestinationType(categoryId, type.name());
        }
    }

    private Category requireCategory(Long id) {
        Category category = id == null ? null : categoryMapper.findById(id);
        if (category == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다.");
        }
        return category;
    }

    private String requiredName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty()) {
            throw new CategoryValidationException("name", "카테고리 이름을 입력해 주세요.");
        }
        if (name.length() > MAX_CATEGORY_NAME_LENGTH) {
            throw new CategoryValidationException("name", "카테고리 이름은 100자 이하로 입력해 주세요.");
        }
        return name;
    }

    /** 같은 타입이 여러 번 들어와도 복합 PK 가 깨지지 않도록 순서를 유지하며 중복을 없앤다. */
    private List<DestinationType> requiredDestinationTypes(List<DestinationType> values) {
        Set<DestinationType> types = new LinkedHashSet<>();
        if (values != null) {
            for (DestinationType type : values) {
                if (type != null) {
                    types.add(type);
                }
            }
        }
        if (types.isEmpty()) {
            throw new CategoryValidationException(
                    "destinationTypes", "적용 대상을 1개 이상 선택해 주세요.");
        }
        return List.copyOf(types);
    }

    private void ensureUniqueName(String name, Long excludeId) {
        if (categoryMapper.countByNameExcludingId(name, excludeId) > 0) {
            throw new DuplicateCategoryNameException();
        }
    }

    public String getFirstCategoryNameByDestinationId(Long destinationId) {
        List<String> categories = categoryMapper.getCategoryNamesByDestinationId(destinationId);
        return categories.isEmpty() ? null : categories.get(0);
    }

    /**
     * 카테고리 삭제.
     * categories -> destination_categories FK 가 ON DELETE CASCADE 라 DB 는 사용 중 삭제를
     * 막아주지 않는다. 그냥 지우면 그 카테고리를 쓰던 여행지의 분류가 조용히 사라지므로,
     * 삭제 전 사용 건수 확인이 유일한 방어선이다.
     * category_destination_types 매핑만 있는 카테고리는 잃을 정보가 없어 삭제를 허용하고,
     * 매핑은 DB CASCADE 로 정리되므로 여기서 따로 지우지 않는다.
     */
    @Transactional
    public void deleteCategory(Long id) {
        requireCategory(id);

        int usageCount = categoryMapper.countDestinationsByCategoryId(id);
        if (usageCount > 0) {
            throw new CategoryInUseException(usageCount);
        }
        categoryMapper.deleteById(id);
    }
}
