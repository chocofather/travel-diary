package com.example.travlediary.service.faq;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.FaqCategoryForm;
import com.example.travlediary.dto.FaqCategoryListItemDto;
import com.example.travlediary.dto.FaqCategoryTranslationForm;
import com.example.travlediary.model.FaqCategory;
import com.example.travlediary.model.FaqCategoryTranslation;
import com.example.travlediary.repository.faq.FaqMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 관리자 FAQ 카테고리 관리.
 *
 * <p>한국어 이름은 faq_categories 원문이고, 나머지 언어는 faq_category_translations 에 담는다.
 * 이름 저장과 번역 저장은 한 트랜잭션에서 끝낸다. (FAQ·공지사항 번역과 같은 정책)
 */
@Service
@RequiredArgsConstructor
public class FaqCategoryService {

    /** faq_categories.category_name 은 varchar(100) */
    private static final int MAX_CATEGORY_NAME_LENGTH = 100;

    /** 번역 슬롯 언어. 한국어는 faq_categories 원문이 대신하므로 여기에서 뺀다. */
    private static final Set<String> SUPPORTED_TRANSLATION_CODES = SupportedLanguage.all().stream()
            .filter(language -> language != SupportedLanguage.KOREAN)
            .map(SupportedLanguage::getLanguageTag)
            .collect(Collectors.toUnmodifiableSet());

    private final FaqMapper faqMapper;

    @Transactional(readOnly = true)
    public List<FaqCategoryListItemDto> getAdminList() {
        return faqMapper.findAdminCategories();
    }

    /** 수정 화면 복원용. 이름과 언어별 번역을 폼에 담아 돌려준다. */
    @Transactional(readOnly = true)
    public FaqCategoryForm getForm(Long id) {
        FaqCategoryForm form = FaqCategoryForm.from(requireCategory(id));
        form.setTranslations(getTranslationForms(id));
        return form;
    }

    /**
     * 수정 화면 복원용 번역 슬롯. 저장된 줄이 있으면 채우고, 없는 언어는 빈 슬롯으로 둔다.
     *
     * <p>슬롯은 언어 코드로 찾아 채운다. 자리 번호나 조회 순서에 뜻을 두지 않는다.
     */
    @Transactional(readOnly = true)
    public List<FaqCategoryTranslationForm> getTranslationForms(Long faqCategoryId) {
        List<FaqCategoryTranslationForm> slots = FaqCategoryTranslationForm.newTranslationSlots();
        if (faqCategoryId == null) {
            return slots;
        }

        Map<String, FaqCategoryTranslationForm> slotsByLanguage = new LinkedHashMap<>();
        for (FaqCategoryTranslationForm slot : slots) {
            slotsByLanguage.putIfAbsent(slot.getLanguageCode(), slot);
        }

        for (FaqCategoryTranslation translation : storedTranslations(faqCategoryId)) {
            FaqCategoryTranslationForm slot = slotsByLanguage.get(translation.getLanguageCode());
            if (slot == null) {
                // 슬롯에 없는 언어가 남아 있어도 화면에는 그리지 않는다.
                continue;
            }
            slot.setCategoryName(
                    translation.getCategoryName() == null ? "" : translation.getCategoryName());
        }
        return slots;
    }

    @Transactional
    public Long create(FaqCategoryForm form) {
        String categoryName = requiredCategoryName(form);
        ensureUniqueName(categoryName, null);

        FaqCategory category = new FaqCategory();
        category.setCategoryName(categoryName);
        try {
            if (faqMapper.insertCategory(category) != 1 || category.getId() == null) {
                throw new IllegalStateException("FAQ 카테고리 저장에 실패했습니다.");
            }
        } catch (DuplicateKeyException exception) {
            // 사전 검사와 INSERT 사이의 경합은 UNIQUE 제약이 잡아 준다.
            throw duplicateName();
        }
        // 이름 저장과 같은 트랜잭션에서 번역까지 끝낸다. 비워 둔 언어는 줄을 만들지 않는다.
        saveTranslations(category.getId(), form.getTranslations());
        return category.getId();
    }

    @Transactional
    public void update(Long id, FaqCategoryForm form) {
        FaqCategory category = requireCategory(id);
        String categoryName = requiredCategoryName(form);
        // 자기 자신은 중복 대상에서 제외한다 (이름을 그대로 두고 저장하는 경우)
        ensureUniqueName(categoryName, id);

        category.setCategoryName(categoryName);
        try {
            if (faqMapper.updateCategory(category) != 1) {
                throw notFound();
            }
        } catch (DuplicateKeyException exception) {
            throw duplicateName();
        }
        // 이름 수정과 같은 트랜잭션에서 번역까지 끝낸다. 비운 언어는 그 줄만 지워진다.
        saveTranslations(id, form.getTranslations());
    }

    /**
     * 카테고리 삭제.
     *
     * <p>FAQ 가 쓰고 있으면 지우지 않는다. FAQ 를 함께 지우거나 다른 카테고리로 옮기지 않는다.
     * 사용 중이 아니면 카테고리만 지우고, 번역은 FK ON DELETE CASCADE 로 정리된다.
     */
    @Transactional
    public void delete(Long id) {
        requireCategory(id);

        int faqCount = faqMapper.countFaqsByCategoryId(id);
        if (faqCount > 0) {
            throw new FaqCategoryInUseException(faqCount);
        }

        try {
            if (faqMapper.deleteCategory(id) != 1) {
                throw notFound();
            }
        } catch (DataIntegrityViolationException exception) {
            // 확인과 삭제 사이에 FAQ 가 생겼을 때는 DB 제약이 막아 준다.
            throw new FaqCategoryInUseException(exception);
        }
    }

    /**
     * 카테고리 이름 번역을 언어 한 줄씩 저장한다.
     *
     * <p>한국어는 faq_categories 원문이 대신하므로 ko 슬롯이 섞여 들어와도 쓰지 않는다.
     * 나머지 언어는 값이 있으면 없던 줄은 INSERT, 있던 줄은 UPDATE 하고,
     * 비우면 그 언어 줄만 DELETE 한다. 다른 언어 줄은 건드리지 않는다.
     */
    private void saveTranslations(Long faqCategoryId,
                                  List<FaqCategoryTranslationForm> translationForms) {
        if (faqCategoryId == null || translationForms == null) {
            return;
        }

        // 기존 줄은 한 번만 읽고 언어 코드로 찾아 쓴다.
        Map<String, FaqCategoryTranslation> existing = new LinkedHashMap<>();
        for (FaqCategoryTranslation translation : storedTranslations(faqCategoryId)) {
            existing.putIfAbsent(translation.getLanguageCode(), translation);
        }

        Set<String> handledLanguages = new HashSet<>();
        for (FaqCategoryTranslationForm form : translationForms) {
            if (form == null || form.getLanguageCode() == null) {
                continue;
            }
            String languageCode = form.getLanguageCode();
            if (!SUPPORTED_TRANSLATION_CODES.contains(languageCode)) {
                // 화면이 정한 슬롯 언어만 저장한다. ko 슬롯과 임의 언어 코드는 무시한다.
                continue;
            }
            if (!handledLanguages.add(languageCode)) {
                // 같은 언어가 두 번 들어오면 앞의 값만 쓴다. (UNIQUE 충돌을 만들지 않는다)
                continue;
            }
            saveTranslation(translationOf(faqCategoryId, form),
                    existing.containsKey(languageCode));
        }
    }

    /** 이름이 없으면 그 언어 줄을 남기지 않는다. */
    private void saveTranslation(FaqCategoryTranslation translation, boolean exists) {
        if (translation.getCategoryName() == null) {
            if (exists) {
                faqMapper.deleteCategoryTranslation(
                        translation.getFaqCategoryId(), translation.getLanguageCode());
            }
            return;
        }
        if (exists) {
            faqMapper.updateCategoryTranslation(translation);
        } else {
            faqMapper.insertCategoryTranslation(translation);
        }
    }

    private FaqCategoryTranslation translationOf(Long faqCategoryId,
                                                 FaqCategoryTranslationForm form) {
        FaqCategoryTranslation translation = new FaqCategoryTranslation();
        translation.setFaqCategoryId(faqCategoryId);
        translation.setLanguageCode(form.getLanguageCode());
        translation.setCategoryName(translationName(form.getCategoryName()));
        return translation;
    }

    /** 번역 이름도 원문과 같은 기준으로 다듬는다. 비어 있는 것은 오류가 아니라 '없음'이다. */
    private String translationName(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }
        String stripped = categoryName.strip();
        if (stripped.length() > MAX_CATEGORY_NAME_LENGTH) {
            throw new FaqValidationException(null, "번역 카테고리명은 100자 이하로 입력해 주세요.");
        }
        return stripped;
    }

    private String requiredCategoryName(FaqCategoryForm form) {
        if (form == null) {
            throw new FaqValidationException("categoryName", "카테고리명을 입력해 주세요.");
        }
        String categoryName = form.getCategoryName() == null ? "" : form.getCategoryName().strip();
        form.setCategoryName(categoryName);
        if (categoryName.isEmpty()) {
            throw new FaqValidationException("categoryName", "카테고리명을 입력해 주세요.");
        }
        if (categoryName.length() > MAX_CATEGORY_NAME_LENGTH) {
            throw new FaqValidationException("categoryName", "카테고리명은 100자 이하로 입력해 주세요.");
        }
        return categoryName;
    }

    private void ensureUniqueName(String categoryName, Long excludeId) {
        if (faqMapper.countCategoriesByNameExcludingId(categoryName, excludeId) > 0) {
            throw duplicateName();
        }
    }

    private FaqValidationException duplicateName() {
        return new FaqValidationException("categoryName", "이미 등록된 카테고리명입니다.");
    }

    /** 언어 코드가 없는 줄은 어느 슬롯에도 맞출 수 없으므로 걸러 낸다. */
    private List<FaqCategoryTranslation> storedTranslations(Long faqCategoryId) {
        List<FaqCategoryTranslation> stored =
                faqMapper.findCategoryTranslationsByCategoryId(faqCategoryId);
        if (stored == null) {
            return List.of();
        }
        List<FaqCategoryTranslation> usable = new ArrayList<>();
        for (FaqCategoryTranslation translation : stored) {
            if (translation != null && translation.getLanguageCode() != null) {
                usable.add(translation);
            }
        }
        return usable;
    }

    private FaqCategory requireCategory(Long id) {
        FaqCategory category = id == null ? null : faqMapper.findCategoryById(id);
        if (category == null) {
            throw notFound();
        }
        return category;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "FAQ 카테고리를 찾을 수 없습니다.");
    }
}
