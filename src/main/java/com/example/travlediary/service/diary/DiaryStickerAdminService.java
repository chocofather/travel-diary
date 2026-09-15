package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryStickerCategoryForm;
import com.example.travlediary.dto.DiaryStickerFilter;
import com.example.travlediary.dto.DiaryStickerForm;
import com.example.travlediary.model.DiaryStickerAccessTier;
import com.example.travlediary.model.DiaryStickerCatalogItem;
import com.example.travlediary.model.DiaryStickerCategoryEntity;
import com.example.travlediary.repository.diary.DiaryStickerMapper;
import com.example.travlediary.service.file.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DiaryStickerAdminService {
    private final DiaryStickerMapper mapper;
    private final FileUploadService fileUploadService;

    @Transactional(readOnly = true)
    public List<DiaryStickerCatalogItem> getStickers(DiaryStickerFilter filter) {
        return mapper.findAdminStickers(filter == null ? new DiaryStickerFilter() : filter);
    }

    @Transactional(readOnly = true)
    public List<DiaryStickerCategoryEntity> getCategories() {
        return mapper.findAllCategories();
    }

    @Transactional(readOnly = true)
    public DiaryStickerForm getStickerForm(Long id) {
        DiaryStickerCatalogItem item = requireSticker(id);
        DiaryStickerForm form = new DiaryStickerForm();
        form.setName(item.getName());
        form.setCategoryId(item.getCategoryId());
        form.setStickerType(item.getStickerType());
        form.setAccessTier(item.getAccessTier());
        form.setVisible(item.isVisible());
        form.setDisplayOrder(item.getDisplayOrder());
        return form;
    }

    @Transactional(readOnly = true)
    public DiaryStickerCatalogItem getSticker(Long id) {
        return requireSticker(id);
    }

    @Transactional
    public void create(DiaryStickerForm form) {
        validate(form, true);
        String imageUrl = fileUploadService.saveDiaryStickerImage(form.getImage(), form.getStickerType());
        boolean cleanupRegistered = registerRollbackCleanup(imageUrl);
        try {
            DiaryStickerCatalogItem item = fromForm(form);
            item.setCatalogKey("admin-" + UUID.randomUUID().toString().toLowerCase(Locale.ROOT));
            item.setImageUrl(imageUrl);
            mapper.insertSticker(item);
        } catch (RuntimeException exception) {
            if (!cleanupRegistered) fileUploadService.deleteDiaryStickerImage(imageUrl);
            throw exception;
        }
    }

    @Transactional
    public void update(Long id, DiaryStickerForm form) {
        DiaryStickerCatalogItem current = requireSticker(id);
        validate(form, false);
        boolean hasNewImage = form.getImage() != null && !form.getImage().isEmpty();
        if (current.getStickerType() != form.getStickerType() && !hasNewImage) {
            throw new DiaryStickerValidationException(
                    "image", "유형을 변경하려면 새 이미지를 선택해 주세요.");
        }

        String newImageUrl = hasNewImage
                ? fileUploadService.saveDiaryStickerImage(form.getImage(), form.getStickerType())
                : null;
        boolean cleanupRegistered = registerRollbackCleanup(newImageUrl);
        try {
            DiaryStickerCatalogItem item = fromForm(form);
            item.setId(id);
            item.setCatalogKey(current.getCatalogKey());
            item.setImageUrl(newImageUrl == null ? current.getImageUrl() : newImageUrl);
            item.setCollectionCode(current.getCollectionCode());
            item.setTapeStyle(current.getTapeStyle());
            item.setRepeatLeftUrl(newImageUrl == null ? current.getRepeatLeftUrl() : null);
            item.setRepeatCenterUrl(newImageUrl == null ? current.getRepeatCenterUrl() : null);
            item.setRepeatRightUrl(newImageUrl == null ? current.getRepeatRightUrl() : null);
            mapper.updateSticker(item);
            if (newImageUrl != null) {
                mapper.insertSticker(imageHistory(current));
            }
        } catch (RuntimeException exception) {
            if (newImageUrl != null && !cleanupRegistered) {
                fileUploadService.deleteDiaryStickerImage(newImageUrl);
            }
            throw exception;
        }
        // 이전 URL은 이미 저장된 다이어리 요소가 계속 사용하므로 삭제하지 않는다.
    }

    private DiaryStickerCatalogItem imageHistory(DiaryStickerCatalogItem current) {
        DiaryStickerCatalogItem history = new DiaryStickerCatalogItem();
        history.setCatalogKey("history-" + UUID.randomUUID().toString().toLowerCase(Locale.ROOT));
        history.setName(current.getName());
        history.setCategoryId(current.getCategoryId());
        history.setImageUrl(current.getImageUrl());
        history.setStickerType(current.getStickerType());
        history.setAccessTier(current.getAccessTier());
        history.setVisible(false);
        history.setDisplayOrder(current.getDisplayOrder());
        history.setCollectionCode(current.getCollectionCode());
        history.setTapeStyle(current.getTapeStyle());
        history.setRepeatLeftUrl(current.getRepeatLeftUrl());
        history.setRepeatCenterUrl(current.getRepeatCenterUrl());
        history.setRepeatRightUrl(current.getRepeatRightUrl());
        return history;
    }

    @Transactional
    public void hide(Long id) {
        requireSticker(id);
        mapper.hideSticker(id);
    }

    @Transactional(readOnly = true)
    public DiaryStickerCategoryForm getCategoryForm(Long id) {
        DiaryStickerCategoryEntity category = requireCategory(id);
        DiaryStickerCategoryForm form = new DiaryStickerCategoryForm();
        form.setName(category.getName());
        form.setDisplayOrder(category.getDisplayOrder());
        form.setVisible(category.isVisible());
        return form;
    }

    @Transactional
    public void createCategory(DiaryStickerCategoryForm form) {
        validateCategory(form, null);
        DiaryStickerCategoryEntity category = categoryFromForm(form);
        category.setCode("category-" + UUID.randomUUID().toString().toLowerCase(Locale.ROOT));
        mapper.insertCategory(category);
    }

    @Transactional
    public void updateCategory(Long id, DiaryStickerCategoryForm form) {
        DiaryStickerCategoryEntity current = requireCategory(id);
        validateCategory(form, id);
        DiaryStickerCategoryEntity category = categoryFromForm(form);
        category.setId(id);
        category.setCode(current.getCode());
        mapper.updateCategory(category);
    }

    private void validate(DiaryStickerForm form, boolean imageRequired) {
        if (form == null) throw invalid(null, "스티커 정보를 입력해 주세요.");
        form.setName(form.getName() == null ? null : form.getName().strip());
        if (form.getName() == null || form.getName().isBlank()) throw invalid("name", "이름을 입력해 주세요.");
        if (form.getName().length() > 100) throw invalid("name", "이름은 100자 이하로 입력해 주세요.");
        if (form.getCategoryId() == null || mapper.findCategoryById(form.getCategoryId()) == null) {
            throw invalid("categoryId", "카테고리를 선택해 주세요.");
        }
        if (form.getStickerType() == null) throw invalid("stickerType", "유형을 선택해 주세요.");
        if (form.getAccessTier() == null) throw invalid("accessTier", "이용등급을 선택해 주세요.");
        if (form.getVisible() == null) throw invalid("visible", "노출 여부를 선택해 주세요.");
        if (form.getDisplayOrder() == null || form.getDisplayOrder() < 1) {
            throw invalid("displayOrder", "표시순서는 1 이상이어야 합니다.");
        }
        if (imageRequired && (form.getImage() == null || form.getImage().isEmpty())) {
            throw invalid("image", "PNG 또는 WebP 이미지를 선택해 주세요.");
        }
    }

    private void validateCategory(DiaryStickerCategoryForm form, Long excludeId) {
        if (form == null) throw invalid(null, "카테고리 정보를 입력해 주세요.");
        form.setName(form.getName() == null ? null : form.getName().strip());
        if (form.getName() == null || form.getName().isBlank()) throw invalid("name", "카테고리명을 입력해 주세요.");
        if (form.getName().length() > 50) throw invalid("name", "카테고리명은 50자 이하로 입력해 주세요.");
        if (form.getDisplayOrder() == null || form.getDisplayOrder() < 1) {
            throw invalid("displayOrder", "표시순서는 1 이상이어야 합니다.");
        }
        if (form.getVisible() == null) throw invalid("visible", "노출 여부를 선택해 주세요.");
        if (mapper.countCategoryName(form.getName(), excludeId) > 0) {
            throw invalid("name", "이미 등록된 카테고리명입니다.");
        }
    }

    private DiaryStickerCatalogItem fromForm(DiaryStickerForm form) {
        DiaryStickerCatalogItem item = new DiaryStickerCatalogItem();
        item.setName(form.getName());
        item.setCategoryId(form.getCategoryId());
        item.setStickerType(form.getStickerType());
        item.setAccessTier(form.getAccessTier() == null ? DiaryStickerAccessTier.FREE : form.getAccessTier());
        item.setVisible(Boolean.TRUE.equals(form.getVisible()));
        item.setDisplayOrder(form.getDisplayOrder());
        item.setCollectionCode("default");
        item.setTapeStyle("NORMAL");
        return item;
    }

    private DiaryStickerCategoryEntity categoryFromForm(DiaryStickerCategoryForm form) {
        DiaryStickerCategoryEntity category = new DiaryStickerCategoryEntity();
        category.setName(form.getName());
        category.setDisplayOrder(form.getDisplayOrder());
        category.setVisible(Boolean.TRUE.equals(form.getVisible()));
        return category;
    }

    private DiaryStickerCatalogItem requireSticker(Long id) {
        DiaryStickerCatalogItem sticker = id == null ? null : mapper.findStickerById(id);
        if (sticker == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "스티커를 찾을 수 없습니다.");
        return sticker;
    }

    private DiaryStickerCategoryEntity requireCategory(Long id) {
        DiaryStickerCategoryEntity category = id == null ? null : mapper.findCategoryById(id);
        if (category == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "스티커 카테고리를 찾을 수 없습니다.");
        return category;
    }

    private DiaryStickerValidationException invalid(String field, String message) {
        return new DiaryStickerValidationException(field, message);
    }

    private boolean registerRollbackCleanup(String imageUrl) {
        if (imageUrl == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    fileUploadService.deleteDiaryStickerImage(imageUrl);
                }
            }
        });
        return true;
    }
}
