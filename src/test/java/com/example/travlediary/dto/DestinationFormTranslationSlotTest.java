package com.example.travlediary.dto;

import com.example.travlediary.model.Destination;
import com.example.travlediary.model.DestinationTranslation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 수정 화면은 translations[0]=한국어, translations[1]=영어 슬롯에 바인딩한다.
 * 번역 조회 SQL 에는 ORDER BY 가 없으므로 Form 생성 단계에서 languageCode 로 슬롯을 고정한다.
 */
class DestinationFormTranslationSlotTest {

    @Test
    void keepsKoreanFirstAndEnglishSecondForTheUsualOrder() {
        DestinationForm form = form(List.of(korean(), english()));

        assertKoreanSlot(form);
        assertEnglishSlot(form);
    }

    @Test
    void keepsKoreanFirstAndEnglishSecondEvenWhenTheQueryReturnsThemReversed() {
        DestinationForm form = form(List.of(english(), korean()));

        assertKoreanSlot(form);
        assertEnglishSlot(form);
    }

    @Test
    void fillsAnEmptyEnglishSlotWhenOnlyKoreanExists() {
        DestinationForm form = form(List.of(korean()));

        assertKoreanSlot(form);
        assertEmptySlot(form.getTranslations().get(1), "en");
    }

    @Test
    void fillsAnEmptyKoreanSlotWhenOnlyEnglishExists() {
        DestinationForm form = form(List.of(english()));

        assertEmptySlot(form.getTranslations().get(0), "ko");
        assertEnglishSlot(form);
    }

    @Test
    void keepsBothSlotsEmptyWhenThereIsNoTranslation() {
        DestinationForm form = form(List.of());

        assertThat(form.getTranslations()).hasSize(2);
        assertEmptySlot(form.getTranslations().get(0), "ko");
        assertEmptySlot(form.getTranslations().get(1), "en");
    }

    @Test
    void ignoresUnsupportedLanguagesWithoutMovingTheKoreanAndEnglishSlots() {
        DestinationForm form = form(List.of(
                translation("ja", "景福宮", "日本語の説明", "日本語の要約"),
                english(),
                korean()));

        assertThat(form.getTranslations()).hasSize(2);
        assertKoreanSlot(form);
        assertEnglishSlot(form);
    }

    @Test
    void keepsTheOtherEditFormFieldsWhileFixingTheTranslationSlots() {
        DestinationDetailDto dto = detailDto();
        dto.setCategoryIds(List.of(4L, 7L));

        DestinationForm form = DestinationForm.fromDetailDto(dto, List.of(english(), korean()));

        assertThat(form.getDestinationId()).isEqualTo(9L);
        assertThat(form.getRegionId()).isEqualTo(31L);
        assertThat(form.getCategoryIds()).containsExactly(4L, 7L);
    }

    private void assertKoreanSlot(DestinationForm form) {
        DestinationTranslationForm slot = form.getTranslations().get(0);
        assertThat(slot.getLanguageCode()).isEqualTo("ko");
        assertThat(slot.getName()).isEqualTo("경복궁");
        assertThat(slot.getDescription()).isEqualTo("한국어 설명");
        assertThat(slot.getShortDescription()).isEqualTo("한국어 요약");
    }

    private void assertEnglishSlot(DestinationForm form) {
        DestinationTranslationForm slot = form.getTranslations().get(1);
        assertThat(slot.getLanguageCode()).isEqualTo("en");
        assertThat(slot.getName()).isEqualTo("Gyeongbokgung Palace");
        assertThat(slot.getDescription()).isEqualTo("English description");
        assertThat(slot.getShortDescription()).isEqualTo("English summary");
    }

    private void assertEmptySlot(DestinationTranslationForm slot, String languageCode) {
        assertThat(slot.getLanguageCode()).isEqualTo(languageCode);
        assertThat(slot.getName()).isEmpty();
        assertThat(slot.getDescription()).isEmpty();
        assertThat(slot.getShortDescription()).isEmpty();
    }

    private DestinationForm form(List<DestinationTranslation> translations) {
        return DestinationForm.fromDetailDto(detailDto(), translations);
    }

    private DestinationDetailDto detailDto() {
        Destination destination = new Destination();
        destination.setId(9L);
        destination.setRegionId(31L);
        DestinationDetailDto dto = new DestinationDetailDto();
        dto.setDestination(destination);
        return dto;
    }

    private DestinationTranslation korean() {
        return translation("ko", "경복궁", "한국어 설명", "한국어 요약");
    }

    private DestinationTranslation english() {
        return translation("en", "Gyeongbokgung Palace", "English description", "English summary");
    }

    private DestinationTranslation translation(String languageCode,
                                               String name,
                                               String description,
                                               String shortDescription) {
        DestinationTranslation translation = new DestinationTranslation();
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        translation.setDescription(description);
        translation.setShortDescription(shortDescription);
        return translation;
    }
}
