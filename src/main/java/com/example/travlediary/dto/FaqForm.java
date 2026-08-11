package com.example.travlediary.dto;

import com.example.travlediary.model.Faq;
import lombok.Data;

@Data
public class FaqForm {
    private Long categoryId;
    private String question;
    private String answer;
    private Long orderIndex = 1L;
    private boolean visible = true;

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
