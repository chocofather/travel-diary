package com.example.travlediary.dto;

import com.example.travlediary.config.i18n.SupportedLanguage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 여행정보의 언어별 입력 슬롯.
 *
 * <p>한국어는 기존 제목·본문 입력이 그대로 base 이자 ko 번역이 되므로 여기에는 담지 않는다.
 * 언어 코드는 화면이 고르지 않고 슬롯에 고정한다. (canonical: en / ja / zh-CN / zh-TW)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TravelInfoTranslationForm {
    private String languageCode;
    private String title;
    private String content;

    public TravelInfoTranslationForm(String languageCode) {
        this.languageCode = languageCode;
    }

    /**
     * canonical 순서(ko / en / ja / zh-CN / zh-TW)로 빈 슬롯을 만든다.
     * 여행정보 폼과 축제·행사 폼이 같은 슬롯 구성을 쓴다.
     */
    public static List<TravelInfoTranslationForm> newTranslationSlots() {
        List<TravelInfoTranslationForm> slots = new ArrayList<>();
        for (SupportedLanguage language : SupportedLanguage.all()) {
            slots.add(new TravelInfoTranslationForm(language.getLanguageTag(), "", ""));
        }
        return slots;
    }
}
