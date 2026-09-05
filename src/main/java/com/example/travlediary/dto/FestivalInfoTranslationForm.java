package com.example.travlediary.dto;

import com.example.travlediary.config.i18n.SupportedLanguage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 관리자 축제·행사 상세정보의 언어별 입력 슬롯.
 *
 * <p>한국어는 기존 행사 상세정보 입력이 그대로 base 이자 ko 번역이 되므로 화면에 그리지 않는다.
 * 언어 코드는 화면이 고르지 않고 슬롯에 고정한다. (canonical: en / ja / zh-CN / zh-TW)
 * 연락처·홈페이지·TourAPI 식별자는 언어와 무관하므로 여기에 담지 않는다.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FestivalInfoTranslationForm {
    private String languageCode;
    private String eventPlace;
    private String address;
    private String playTime;
    private String useTime;
    private String sponsor1;
    private String sponsor2;

    public FestivalInfoTranslationForm(String languageCode) {
        this.languageCode = languageCode;
    }

    /** canonical 순서(ko / en / ja / zh-CN / zh-TW)로 빈 슬롯을 만든다. */
    public static List<FestivalInfoTranslationForm> newTranslationSlots() {
        List<FestivalInfoTranslationForm> slots = new ArrayList<>();
        for (SupportedLanguage language : SupportedLanguage.all()) {
            slots.add(new FestivalInfoTranslationForm(
                    language.getLanguageTag(), "", "", "", "", "", ""));
        }
        return slots;
    }
}
