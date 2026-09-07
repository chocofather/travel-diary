package com.example.travlediary.dto;

import com.example.travlediary.model.FaqCategory;
import lombok.Data;

import java.util.List;

/**
 * 관리자 자주 묻는 질문 카테고리 등록/수정 폼.
 * 한국어 이름은 faq_categories 원문이고, 나머지 언어는 선택 입력이다.
 */
@Data
public class FaqCategoryForm {
    private String categoryName;

    /**
     * faq_category_translations. 언어별 카테고리명 입력 슬롯이다.
     * 0번은 한국어 자리이고 화면에 그리지 않는다.
     */
    private List<FaqCategoryTranslationForm> translations =
            FaqCategoryTranslationForm.newTranslationSlots();

    public static FaqCategoryForm from(FaqCategory category) {
        FaqCategoryForm form = new FaqCategoryForm();
        form.setCategoryName(category.getCategoryName());
        return form;
    }
}
