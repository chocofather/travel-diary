package com.example.travlediary.dto;

import com.example.travlediary.model.Faq;
import lombok.Data;

import java.util.List;

@Data
public class FaqForm {
    private Long categoryId;
    private String question;
    private String answer;
    private Long orderIndex = 1L;
    private boolean visible = true;

    /**
     * faq_translations. 언어별 질문·답변 입력 슬롯이다.
     * 0번은 한국어 자리이고 화면에 그리지 않는다 — 한국어는 위의 question/answer 가 그대로 원문이다.
     */
    private List<FaqTranslationForm> translations = FaqTranslationForm.newTranslationSlots();

    public static FaqForm from(Faq faq) {
        FaqForm form = new FaqForm();
        form.setCategoryId(faq.getCategoryId());
        form.setQuestion(faq.getQuestion());
        form.setAnswer(faq.getAnswer());
        form.setOrderIndex(faq.getOrderIndex());
        form.setVisible(Boolean.TRUE.equals(faq.getIsVisible()));
        return form;
    }
}
