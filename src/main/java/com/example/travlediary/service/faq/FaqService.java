package com.example.travlediary.service.faq;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.FaqForm;
import com.example.travlediary.dto.FaqListItemDto;
import com.example.travlediary.dto.FaqTranslationForm;
import com.example.travlediary.model.Faq;
import com.example.travlediary.model.FaqCategory;
import com.example.travlediary.model.FaqTranslation;
import com.example.travlediary.repository.faq.FaqMapper;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class FaqService {

    /** 질문 길이 제한. 한국어 원문과 번역이 같은 기준을 쓴다. (faqs.question / faq_translations.question) */
    private static final int MAX_QUESTION_LENGTH = 255;

    /** 번역 슬롯 언어. 한국어는 faqs 원문이 대신하므로 여기에서 뺀다. */
    private static final Set<String> SUPPORTED_TRANSLATION_CODES = SupportedLanguage.all().stream()
            .filter(language -> language != SupportedLanguage.KOREAN)
            .map(SupportedLanguage::getLanguageTag)
            .collect(Collectors.toUnmodifiableSet());

    private final FaqMapper faqMapper;
    private final FaqLocalizationService faqLocalizationService;
    private final FaqCategoryLocalizationService faqCategoryLocalizationService;

    @Transactional(readOnly = true)
    public List<FaqListItemDto> getAdminList() {
        return faqMapper.findAdminList();
    }

    @Transactional(readOnly = true)
    public List<FaqListItemDto> getPublicList() {
        return faqMapper.findPublicList();
    }

    @Transactional(readOnly = true)
    public List<FaqCategory> getCategories() {
        return faqMapper.findCategories();
    }

    /**
     * 공개 목록의 질문·답변을 요청 언어로 바꿔 둔다.
     *
     * <p>질문·답변 번역과 카테고리 이름 번역을 각각 <b>한 번의 조회</b>로 모아 읽는다.
     * 조회 결과 DTO 는 이 요청에서만 쓰는 값이라 표시할 값을 그대로 담아도
     * 관리자 화면이 읽는 원문에는 영향이 없다. 노출 여부·정렬·번호는 건드리지 않는다.
     */
    @Transactional(readOnly = true)
    public void localizePublicList(List<FaqListItemDto> faqs,
                                   SupportedLanguage requestedLanguage) {
        if (faqs == null || faqs.isEmpty()) {
            // 볼 질문이 없으면 번역도 읽지 않는다.
            return;
        }

        // 카테고리 뱃지는 번호로만 정한다. 표시 이름·번역·언어와 무관하다.
        Map<Long, String> baseQuestions = new LinkedHashMap<>();
        Map<Long, String> baseAnswers = new LinkedHashMap<>();
        Map<Long, String> baseCategoryNames = new LinkedHashMap<>();
        for (FaqListItemDto item : faqs) {
            if (item == null) {
                continue;
            }
            item.setCategoryBadge(FaqCategoryBadge.of(item.getCategoryId()));
            if (item.getId() != null) {
                baseQuestions.putIfAbsent(item.getId(), item.getQuestion());
                baseAnswers.putIfAbsent(item.getId(), item.getAnswer());
            }
            if (item.getCategoryId() != null) {
                // 같은 카테고리를 여러 FAQ 가 써도 번호는 한 번만 담는다.
                baseCategoryNames.putIfAbsent(item.getCategoryId(), item.getCategoryName());
            }
        }
        if (baseQuestions.isEmpty() && baseCategoryNames.isEmpty()) {
            return;
        }

        Map<Long, FaqTranslation> localized = faqLocalizationService
                .resolveLocalizedTextsByFaqIds(baseQuestions, baseAnswers, requestedLanguage);
        Map<Long, String> localizedCategoryNames = faqCategoryLocalizationService
                .resolveLocalizedNamesByCategoryIds(baseCategoryNames, requestedLanguage);
        for (FaqListItemDto item : faqs) {
            if (item == null) {
                continue;
            }
            FaqTranslation display = item.getId() == null ? null : localized.get(item.getId());
            if (display != null) {
                item.setQuestion(display.getQuestion());
                item.setAnswer(display.getAnswer());
            }
            String categoryName = item.getCategoryId() == null
                    ? null
                    : localizedCategoryNames.get(item.getCategoryId());
            if (categoryName != null) {
                item.setCategoryName(categoryName);
            }
        }
    }

    @Transactional(readOnly = true)
    public FaqForm getForm(Long id) {
        FaqForm form = FaqForm.from(requireFaq(faqMapper.findById(id)));
        form.setTranslations(getTranslationForms(id));
        return form;
    }

    /**
     * 수정 화면 복원용 번역 슬롯. 저장된 줄이 있으면 채우고, 없는 언어는 빈 슬롯으로 둔다.
     *
     * <p>슬롯은 언어 코드로 찾아 채운다. 자리 번호나 조회 순서에 뜻을 두지 않는다.
     */
    @Transactional(readOnly = true)
    public List<FaqTranslationForm> getTranslationForms(Long faqId) {
        List<FaqTranslationForm> slots = FaqTranslationForm.newTranslationSlots();
        if (faqId == null) {
            return slots;
        }

        Map<String, FaqTranslationForm> slotsByLanguage = new LinkedHashMap<>();
        for (FaqTranslationForm slot : slots) {
            slotsByLanguage.putIfAbsent(slot.getLanguageCode(), slot);
        }

        for (FaqTranslation translation : storedTranslations(faqId)) {
            FaqTranslationForm slot = slotsByLanguage.get(translation.getLanguageCode());
            if (slot == null) {
                // 슬롯에 없는 언어가 남아 있어도 화면에는 그리지 않는다.
                continue;
            }
            slot.setQuestion(translation.getQuestion() == null ? "" : translation.getQuestion());
            slot.setAnswer(translation.getAnswer() == null ? "" : translation.getAnswer());
        }
        return slots;
    }

    @Transactional
    public Long create(FaqForm form, Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("관리자 정보를 확인할 수 없습니다.");
        }
        ValidatedFaq validated = validate(form);
        Faq faq = new Faq();
        apply(faq, form, validated);
        faq.setUserId(userId);

        if (faqMapper.insertFaq(faq) != 1 || faq.getId() == null) {
            throw new IllegalStateException("자주 묻는 질문 저장에 실패했습니다.");
        }
        // 원문 저장과 같은 트랜잭션에서 번역까지 끝낸다. 비워 둔 언어는 줄을 만들지 않는다.
        saveTranslations(faq.getId(), form.getTranslations());
        return faq.getId();
    }

    @Transactional
    public void update(Long id, FaqForm form) {
        Faq faq = requireFaq(faqMapper.findByIdForUpdate(id));
        ValidatedFaq validated = validate(form);
        apply(faq, form, validated);
        if (faqMapper.updateFaq(faq) != 1) {
            throw notFound();
        }
        // 원문 수정과 같은 트랜잭션에서 번역까지 끝낸다. 비운 언어는 그 줄만 지워진다.
        saveTranslations(id, form.getTranslations());
    }

    @Transactional
    public void delete(Long id) {
        requireFaq(faqMapper.findByIdForUpdate(id));
        if (faqMapper.deleteFaq(id) != 1) {
            throw notFound();
        }
    }

    private ValidatedFaq validate(FaqForm form) {
        if (form == null) {
            throw new FaqValidationException(null, "자주 묻는 질문을 입력해 주세요.");
        }

        String question = form.getQuestion() == null ? "" : form.getQuestion().strip();
        form.setQuestion(question);
        if (question.isEmpty()) {
            throw new FaqValidationException("question", "질문을 입력해 주세요.");
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new FaqValidationException("question", "질문은 255자 이하로 입력해 주세요.");
        }

        String answer = form.getAnswer() == null ? "" : form.getAnswer().strip();
        form.setAnswer(answer);
        if (answer.isEmpty()) {
            throw new FaqValidationException("answer", "답변을 입력해 주세요.");
        }

        if (form.getOrderIndex() == null || form.getOrderIndex() < 1) {
            throw new FaqValidationException("orderIndex", "노출 순서는 1 이상으로 입력해 주세요.");
        }

        if (form.getCategoryId() == null || faqMapper.findCategoryById(form.getCategoryId()) == null) {
            throw new FaqValidationException("categoryId", "유효한 카테고리를 선택해 주세요.");
        }

        return new ValidatedFaq(question, answer);
    }

    private void apply(Faq faq, FaqForm form, ValidatedFaq validated) {
        faq.setCategoryId(form.getCategoryId());
        faq.setQuestion(validated.question());
        faq.setAnswer(validated.answer());
        faq.setOrderIndex(form.getOrderIndex());
        faq.setIsVisible(form.isVisible());
    }

    /**
     * 자주 묻는 질문의 번역을 언어 한 줄씩 저장한다. (공지사항 번역과 같은 정책)
     *
     * <p>한국어는 faqs 원문이 대신하므로 ko 슬롯이 섞여 들어와도 쓰지 않는다.
     * 나머지 언어는 질문·답변 중 하나라도 값이 있으면 없던 줄은 INSERT, 있던 줄은 UPDATE 하고,
     * 둘 다 비우면 그 언어 줄만 DELETE 한다. 다른 언어 줄은 건드리지 않는다.
     */
    private void saveTranslations(Long faqId, List<FaqTranslationForm> translationForms) {
        if (faqId == null || translationForms == null) {
            return;
        }

        // 기존 줄은 한 번만 읽고 언어 코드로 찾아 쓴다.
        Map<String, FaqTranslation> existing = new LinkedHashMap<>();
        for (FaqTranslation translation : storedTranslations(faqId)) {
            existing.putIfAbsent(translation.getLanguageCode(), translation);
        }

        Set<String> handledLanguages = new HashSet<>();
        for (FaqTranslationForm form : translationForms) {
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
            saveTranslation(translationOf(faqId, form), existing.containsKey(languageCode));
        }
    }

    /** 질문·답변이 모두 없으면 그 언어 줄을 남기지 않는다. */
    private void saveTranslation(FaqTranslation translation, boolean exists) {
        if (translation.getQuestion() == null && translation.getAnswer() == null) {
            if (exists) {
                faqMapper.deleteTranslation(translation.getFaqId(), translation.getLanguageCode());
            }
            return;
        }
        if (exists) {
            faqMapper.updateTranslation(translation);
        } else {
            faqMapper.insertTranslation(translation);
        }
    }

    private FaqTranslation translationOf(Long faqId, FaqTranslationForm form) {
        FaqTranslation translation = new FaqTranslation();
        translation.setFaqId(faqId);
        translation.setLanguageCode(form.getLanguageCode());
        translation.setQuestion(translationQuestion(form.getQuestion()));
        translation.setAnswer(translationAnswer(form.getAnswer()));
        return translation;
    }

    /** 번역 질문도 원문과 같은 기준으로 다듬는다. 비어 있는 것은 오류가 아니라 '없음'이다. */
    private String translationQuestion(String question) {
        String stripped = strippedOrNull(question);
        if (stripped != null && stripped.length() > MAX_QUESTION_LENGTH) {
            throw new FaqValidationException(null, "번역 질문은 255자 이하로 입력해 주세요.");
        }
        return stripped;
    }

    /**
     * 번역 답변도 원문과 같이 서식 없는 일반 텍스트다.
     * 앞뒤 공백만 덜어내고 줄바꿈 등 내부 서식은 그대로 둔다. (HTML 정제 대상이 아니다)
     */
    private String translationAnswer(String answer) {
        return strippedOrNull(answer);
    }

    private String strippedOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    /** 언어 코드가 없는 줄은 어느 슬롯에도 맞출 수 없으므로 걸러 낸다. */
    private List<FaqTranslation> storedTranslations(Long faqId) {
        List<FaqTranslation> stored = faqMapper.findTranslationsByFaqId(faqId);
        if (stored == null) {
            return List.of();
        }
        List<FaqTranslation> usable = new ArrayList<>();
        for (FaqTranslation translation : stored) {
            if (translation != null && translation.getLanguageCode() != null) {
                usable.add(translation);
            }
        }
        return usable;
    }

    private Faq requireFaq(Faq faq) {
        if (faq == null) {
            throw notFound();
        }
        return faq;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "자주 묻는 질문을 찾을 수 없습니다.");
    }

    private record ValidatedFaq(String question, String answer) {
    }
}
