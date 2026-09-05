package com.example.travlediary.service.category;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.InfoCategoryForm;
import com.example.travlediary.dto.InfoCategoryTranslationForm;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.InfoCategoryTranslation;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.repository.category.InfoCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InfoCategoryService {

    private static final String KOREAN_CODE = SupportedLanguage.KOREAN.getLanguageTag();

    /** 번역 슬롯 언어. 한국어는 base 입력이 대신하므로 여기에서 뺀다. */
    private static final Set<String> SUPPORTED_TRANSLATION_CODES = SupportedLanguage.all().stream()
            .filter(language -> language != SupportedLanguage.KOREAN)
            .map(SupportedLanguage::getLanguageTag)
            .collect(Collectors.toUnmodifiableSet());

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
        // base 저장과 같은 트랜잭션에서 번역까지 끝낸다. ko 는 base 이름으로 맞춰진다.
        saveTranslations(category.getId(), name, form.getTranslations());
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
        // base 수정과 같은 트랜잭션에서 번역까지 끝낸다. 비운 언어는 그 줄만 지워진다.
        saveTranslations(id, name, form.getTranslations());
    }

    /**
     * 관리자 수정 화면 복원용. 저장된 줄이 있으면 슬롯에 채우고, 없으면 빈 슬롯을 돌려준다.
     *
     * <p>슬롯은 언어 코드로 찾아 채운다. 자리 번호나 조회 순서에 뜻을 두지 않는다.
     */
    @Transactional(readOnly = true)
    public List<InfoCategoryTranslationForm> getTranslationForms(Long categoryId) {
        List<InfoCategoryTranslationForm> slots =
                InfoCategoryTranslationForm.newTranslationSlots();
        if (categoryId == null) {
            return slots;
        }

        Map<String, InfoCategoryTranslationForm> slotsByLanguage = new LinkedHashMap<>();
        for (InfoCategoryTranslationForm slot : slots) {
            slotsByLanguage.putIfAbsent(slot.getLanguageCode(), slot);
        }

        List<InfoCategoryTranslation> stored =
                infoCategoryMapper.findTranslationsByCategoryId(categoryId);
        if (stored != null) {
            for (InfoCategoryTranslation translation : stored) {
                if (translation == null || translation.getLanguageCode() == null) {
                    continue;
                }
                InfoCategoryTranslationForm slot =
                        slotsByLanguage.get(translation.getLanguageCode());
                if (slot == null) {
                    // 슬롯에 없는 언어가 남아 있어도 화면에는 그리지 않는다.
                    continue;
                }
                slot.setName(translation.getName() == null ? "" : translation.getName());
            }
        }
        return slots;
    }

    /**
     * 카테고리 이름 번역을 언어 한 줄씩 저장한다.
     *
     * <p>한국어는 화면의 카테고리명 입력이 그대로 ko 줄이 된다. 그래서 번역 입력에 ko 슬롯이
     * 섞여 들어와도 쓰지 않는다 — base 를 번역 입력으로 덮어쓰는 길은 두지 않는다.
     *
     * <p>나머지 언어는 값이 있으면 남기고, 비면 그 언어 줄만 지운다.
     */
    @Transactional
    public void saveTranslations(Long categoryId,
                                 String baseName,
                                 List<InfoCategoryTranslationForm> translationForms) {
        if (categoryId == null) {
            return;
        }

        // 기존 줄은 한 번만 읽고 언어 코드로 찾아 쓴다.
        Map<String, InfoCategoryTranslation> existing = new LinkedHashMap<>();
        List<InfoCategoryTranslation> stored =
                infoCategoryMapper.findTranslationsByCategoryId(categoryId);
        if (stored != null) {
            for (InfoCategoryTranslation translation : stored) {
                if (translation != null && translation.getLanguageCode() != null) {
                    existing.putIfAbsent(translation.getLanguageCode(), translation);
                }
            }
        }

        saveTranslation(translationOf(categoryId, KOREAN_CODE, baseName),
                existing.containsKey(KOREAN_CODE));

        if (translationForms == null) {
            return;
        }
        Set<String> handledLanguages = new HashSet<>();
        for (InfoCategoryTranslationForm form : translationForms) {
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
            saveTranslation(translationOf(categoryId, languageCode, form.getName()),
                    existing.containsKey(languageCode));
        }
    }

    /** 값이 아예 없으면 그 언어 줄을 남기지 않는다. */
    private void saveTranslation(InfoCategoryTranslation translation, boolean exists) {
        if (translation.getName() == null) {
            if (exists) {
                infoCategoryMapper.deleteTranslation(
                        translation.getInfoCategoryId(), translation.getLanguageCode());
            }
            return;
        }
        if (exists) {
            infoCategoryMapper.updateTranslation(translation);
        } else {
            infoCategoryMapper.insertTranslation(translation);
        }
    }

    private InfoCategoryTranslation translationOf(Long categoryId,
                                                  String languageCode,
                                                  String name) {
        InfoCategoryTranslation translation = new InfoCategoryTranslation();
        translation.setInfoCategoryId(categoryId);
        translation.setLanguageCode(languageCode);
        translation.setName(name == null || name.isBlank() ? null : name.strip());
        return translation;
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
