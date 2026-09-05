package com.example.travlediary.dto;

import com.example.travlediary.config.i18n.SupportedLanguage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 정보 카테고리의 언어별 입력 슬롯.
 *
 * <p>한국어는 기존 카테고리명 입력이 그대로 base 이자 ko 번역이 되므로 화면에 그리지 않는다.
 * 언어 코드는 화면이 고르지 않고 슬롯에 고정한다. (canonical: en / ja / zh-CN / zh-TW)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InfoCategoryTranslationForm {
    private String languageCode;
    private String name;

    /** canonical 순서(ko / en / ja / zh-CN / zh-TW)로 빈 슬롯을 만든다. */
    public static List<InfoCategoryTranslationForm> newTranslationSlots() {
        List<InfoCategoryTranslationForm> slots = new ArrayList<>();
        for (SupportedLanguage language : SupportedLanguage.all()) {
            slots.add(new InfoCategoryTranslationForm(language.getLanguageTag(), ""));
        }
        return slots;
    }
}
