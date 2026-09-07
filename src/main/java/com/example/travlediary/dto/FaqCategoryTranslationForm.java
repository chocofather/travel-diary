package com.example.travlediary.dto;

import com.example.travlediary.config.i18n.SupportedLanguage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 자주 묻는 질문 카테고리의 언어별 입력 슬롯.
 *
 * <p>한국어는 기존 카테고리명 입력이 그대로 원문이므로 여기에는 담지 않는다.
 * 언어 코드는 화면이 고르지 않고 슬롯에 고정한다. (canonical: en / ja / zh-CN / zh-TW)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FaqCategoryTranslationForm {
    private String languageCode;
    private String categoryName;

    /** canonical 순서(ko / en / ja / zh-CN / zh-TW)로 빈 슬롯을 만든다. 0번(ko)은 그리지 않는다. */
    public static List<FaqCategoryTranslationForm> newTranslationSlots() {
        List<FaqCategoryTranslationForm> slots = new ArrayList<>();
        for (SupportedLanguage language : SupportedLanguage.all()) {
            slots.add(new FaqCategoryTranslationForm(language.getLanguageTag(), ""));
        }
        return slots;
    }
}
