package com.example.travlediary.service.faq;

import com.example.travlediary.dto.FaqForm;
import com.example.travlediary.dto.FaqListItemDto;
import com.example.travlediary.model.Faq;
import com.example.travlediary.model.FaqCategory;
import com.example.travlediary.repository.faq.FaqMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FaqService {

    private final FaqMapper faqMapper;

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

    @Transactional(readOnly = true)
    public FaqForm getForm(Long id) {
        return FaqForm.from(requireFaq(faqMapper.findById(id)));
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
        if (question.length() > 255) {
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
