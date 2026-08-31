package com.example.travlediary.service.category;

import com.example.travlediary.dto.InfoCategoryForm;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.repository.category.InfoCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InfoCategoryService {

    private final InfoCategoryMapper infoCategoryMapper;

    @Transactional(readOnly = true)
    public List<InfoCategory> getAll() {
        return infoCategoryMapper.findAll();
    }

    @Transactional(readOnly = true)
    public List<InfoCategory> getVisible() {
        return infoCategoryMapper.findVisible();
    }

    @Transactional(readOnly = true)
    public List<InfoCategory> getVisibleByContentType(TravelInfoContentType contentType) {
        return infoCategoryMapper.findVisibleByContentType(contentType);
    }

    @Transactional(readOnly = true)
    public InfoCategory getById(Long id) {
        return requireCategory(id);
    }

    @Transactional
    public void create(InfoCategoryForm form) {
        String name = normalizeAndValidate(form);
        ensureUniqueName(name, null);

        InfoCategory category = new InfoCategory();
        category.setName(name);
        category.setContentType(form.getContentType());
        category.setDisplayOrder(form.getDisplayOrder());
        category.setIsVisible(form.getIsVisible());

        try {
            infoCategoryMapper.insert(category);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateInfoCategoryNameException(exception);
        }
    }

    @Transactional
    public void update(Long id, InfoCategoryForm form) {
        InfoCategory category = requireCategory(id);
        String name = normalizeAndValidate(form);
        ensureUniqueName(name, id);

        category.setName(name);
        category.setContentType(form.getContentType());
        category.setDisplayOrder(form.getDisplayOrder());
        category.setIsVisible(form.getIsVisible());

        try {
            infoCategoryMapper.update(category);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateInfoCategoryNameException(exception);
        }
    }

    @Transactional
    public void delete(Long id) {
        requireCategory(id);
        if (infoCategoryMapper.countTravelInfoByCategoryId(id) > 0) {
            throw new InfoCategoryInUseException();
        }

        try {
            if (infoCategoryMapper.deleteById(id) != 1) {
                throw notFound();
            }
        } catch (DataIntegrityViolationException exception) {
            throw new InfoCategoryInUseException(exception);
        }
    }

    private InfoCategory requireCategory(Long id) {
        InfoCategory category = id == null ? null : infoCategoryMapper.findById(id);
        if (category == null) {
            throw notFound();
        }
        return category;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "정보 카테고리를 찾을 수 없습니다.");
    }

    private String normalizeAndValidate(InfoCategoryForm form) {
        if (form == null) {
            throw new IllegalArgumentException("카테고리 정보를 입력해 주세요.");
        }

        String name = form.getName() == null ? null : form.getName().strip();
        form.setName(name);

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("카테고리명을 입력해 주세요.");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("카테고리명은 100자 이하로 입력해 주세요.");
        }
        if (form.getContentType() == null) {
            throw new IllegalArgumentException("여행정보 유형을 선택해 주세요.");
        }
        if (form.getDisplayOrder() == null || form.getDisplayOrder() < 1) {
            throw new IllegalArgumentException("표시 순서는 1 이상이어야 합니다.");
        }
        if (form.getIsVisible() == null) {
            throw new IllegalArgumentException("노출 여부를 선택해 주세요.");
        }
        return name;
    }

    private void ensureUniqueName(String name, Long excludeId) {
        if (infoCategoryMapper.countByNameExcludingId(name, excludeId) > 0) {
            throw new DuplicateInfoCategoryNameException();
        }
    }
}
