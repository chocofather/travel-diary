package com.example.travlediary.service.info;

import com.example.travlediary.dto.AttractionInfoTranslationForm;
import com.example.travlediary.model.AttractionInfo;
import com.example.travlediary.model.AttractionInfoTranslation;
import com.example.travlediary.repository.info.AttractionInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 관광지 상세정보 저장: 한국어는 원본이자 ko 번역, 나머지 네 언어는 슬롯 값 그대로.
 *
 * <p>언어 한 줄이 단위이며 legacy 'zh' 는 쓰지 않는다. (식당과 같은 규칙)
 */
@ExtendWith(MockitoExtension.class)
class AttractionInfoAdminTranslationTest {

    @Mock private AttractionInfoMapper attractionInfoMapper;
    @InjectMocks private AttractionInfoService attractionInfoService;

    private AttractionInfo koreanBase;

    @BeforeEach
    void setUpBase() {
        koreanBase = new AttractionInfo();
        koreanBase.setDestinationId(15L);
        koreanBase.setClosedDays("매주 화요일");
        koreanBase.setOpeningHours("09:00~18:00");
        koreanBase.setAdmissionFee("어른 3,000원");
        koreanBase.setGuide("입장 마감은 30분 전");
        // 번역 대상이 아닌 값들
        koreanBase.setParkingAvailable(true);
        koreanBase.setContactNumber("02-3700-3900");
        koreanBase.setHomepageUrl("https://example.com");
    }

    @Test
    void newAttractionStoresTheFiveCanonicalRowsWithoutLegacyChinese() {
        when(attractionInfoMapper.findTranslationsByDestinationId(15L)).thenReturn(List.of());

        attractionInfoService.saveTranslations(15L, koreanBase, filledSlots());

        ArgumentCaptor<AttractionInfoTranslation> captor =
                ArgumentCaptor.forClass(AttractionInfoTranslation.class);
        verify(attractionInfoMapper, times(5)).insertTranslation(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AttractionInfoTranslation::getLanguageCode,
                        AttractionInfoTranslation::getClosedDays)
                .containsExactly(
                        tuple("ko", "매주 화요일"),
                        tuple("en", "Every Tuesday"),
                        tuple("ja", "毎週火曜日"),
                        tuple("zh-CN", "每周二"),
                        tuple("zh-TW", "每週二"));
        assertThat(captor.getAllValues())
                .extracting(AttractionInfoTranslation::getLanguageCode).doesNotContain("zh");
        // 한국어 줄은 원본 네 칸을 그대로 옮긴다
        AttractionInfoTranslation korean = captor.getAllValues().get(0);
        assertThat(korean.getOpeningHours()).isEqualTo("09:00~18:00");
        assertThat(korean.getAdmissionFee()).isEqualTo("어른 3,000원");
        assertThat(korean.getGuide()).isEqualTo("입장 마감은 30분 전");
        verify(attractionInfoMapper, never()).deleteTranslation(any(), any());
    }

    @Test
    void existingLanguagesAreUpdatedIndependently() {
        when(attractionInfoMapper.findTranslationsByDestinationId(15L)).thenReturn(List.of(
                stored("ko", "매주 화요일"), stored("en", "Every Tuesday"), stored("zh-CN", "每周二")));

        List<AttractionInfoTranslationForm> slots = filledSlots();
        slots.get(0).setClosedDays("Closed on Tuesdays");

        attractionInfoService.saveTranslations(15L, koreanBase, slots);

        ArgumentCaptor<AttractionInfoTranslation> updated =
                ArgumentCaptor.forClass(AttractionInfoTranslation.class);
        verify(attractionInfoMapper, times(3)).updateTranslation(updated.capture());
        assertThat(updated.getAllValues())
                .extracting(AttractionInfoTranslation::getLanguageCode,
                        AttractionInfoTranslation::getClosedDays)
                .containsExactly(
                        tuple("ko", "매주 화요일"),
                        tuple("en", "Closed on Tuesdays"),
                        tuple("zh-CN", "每周二"));
        ArgumentCaptor<AttractionInfoTranslation> inserted =
                ArgumentCaptor.forClass(AttractionInfoTranslation.class);
        verify(attractionInfoMapper, times(2)).insertTranslation(inserted.capture());
        assertThat(inserted.getAllValues())
                .extracting(AttractionInfoTranslation::getLanguageCode)
                .containsExactly("ja", "zh-TW");
        verify(attractionInfoMapper, never()).deleteTranslation(any(), any());
    }

    @Test
    void clearingOneLanguageDeletesOnlyThatRow() {
        when(attractionInfoMapper.findTranslationsByDestinationId(15L)).thenReturn(List.of(
                stored("ko", "매주 화요일"), stored("en", "Every Tuesday"), stored("ja", "毎週火曜日")));

        List<AttractionInfoTranslationForm> slots = filledSlots();
        AttractionInfoTranslationForm japanese = slots.get(1);
        japanese.setClosedDays("   ");
        japanese.setOpeningHours(null);
        japanese.setAdmissionFee("");
        japanese.setGuide(null);

        attractionInfoService.saveTranslations(15L, koreanBase, slots);

        verify(attractionInfoMapper).deleteTranslation(15L, "ja");
        verify(attractionInfoMapper, never()).deleteTranslation(15L, "en");
        verify(attractionInfoMapper, never()).deleteTranslation(15L, "ko");
        verify(attractionInfoMapper, times(2)).updateTranslation(any());  // ko, en
        verify(attractionInfoMapper, times(2)).insertTranslation(any());  // zh-CN, zh-TW
    }

    @Test
    void anEmptyKoreanBaseRemovesTheKoreanRowOnly() {
        AttractionInfo emptyBase = new AttractionInfo();
        emptyBase.setDestinationId(15L);
        emptyBase.setContactNumber("02-3700-3900");
        when(attractionInfoMapper.findTranslationsByDestinationId(15L))
                .thenReturn(List.of(stored("ko", "매주 화요일"), stored("en", "Every Tuesday")));

        attractionInfoService.saveTranslations(15L, emptyBase, filledSlots());

        verify(attractionInfoMapper).deleteTranslation(15L, "ko");
        verify(attractionInfoMapper, never()).deleteTranslation(15L, "en");
    }

    @Test
    void unknownLanguageSlotsAreIgnored() {
        when(attractionInfoMapper.findTranslationsByDestinationId(15L)).thenReturn(List.of());
        AttractionInfoTranslationForm legacy = new AttractionInfoTranslationForm("zh");
        legacy.setClosedDays("每周二");

        attractionInfoService.saveTranslations(15L, koreanBase, List.of(legacy));

        ArgumentCaptor<AttractionInfoTranslation> captor =
                ArgumentCaptor.forClass(AttractionInfoTranslation.class);
        verify(attractionInfoMapper, times(1)).insertTranslation(captor.capture());
        assertThat(captor.getValue().getLanguageCode()).isEqualTo("ko");
    }

    @Test
    void editFormRestoresStoredLanguagesIntoTheirOwnSlots() {
        AttractionInfoTranslation english = stored("en", "Every Tuesday");
        english.setOpeningHours("09:00-18:00");
        english.setAdmissionFee("KRW 3,000");
        english.setGuide("Last admission 30 minutes before closing");
        when(attractionInfoMapper.findTranslationsByDestinationId(15L))
                .thenReturn(List.of(stored("ko", "매주 화요일"), english, stored("zh-TW", "每週二")));

        List<AttractionInfoTranslationForm> slots =
                attractionInfoService.getTranslationForms(15L);

        assertThat(slots).extracting(AttractionInfoTranslationForm::getLanguageCode)
                .containsExactly("en", "ja", "zh-CN", "zh-TW");
        assertThat(slots.get(0).getClosedDays()).isEqualTo("Every Tuesday");
        assertThat(slots.get(0).getOpeningHours()).isEqualTo("09:00-18:00");
        assertThat(slots.get(0).getAdmissionFee()).isEqualTo("KRW 3,000");
        assertThat(slots.get(0).getGuide()).isEqualTo("Last admission 30 minutes before closing");
        assertThat(slots.get(1).getClosedDays()).isNull();
        assertThat(slots.get(2).getClosedDays()).isNull();
        assertThat(slots.get(3).getClosedDays()).isEqualTo("每週二");
    }

    private List<AttractionInfoTranslationForm> filledSlots() {
        List<AttractionInfoTranslationForm> slots = new ArrayList<>();
        slots.add(slot("en", "Every Tuesday"));
        slots.add(slot("ja", "毎週火曜日"));
        slots.add(slot("zh-CN", "每周二"));
        slots.add(slot("zh-TW", "每週二"));
        return slots;
    }

    private AttractionInfoTranslationForm slot(String languageCode, String closedDays) {
        AttractionInfoTranslationForm slot = new AttractionInfoTranslationForm(languageCode);
        slot.setClosedDays(closedDays);
        return slot;
    }

    private AttractionInfoTranslation stored(String languageCode, String closedDays) {
        AttractionInfoTranslation translation = new AttractionInfoTranslation();
        translation.setDestinationId(15L);
        translation.setLanguageCode(languageCode);
        translation.setClosedDays(closedDays);
        return translation;
    }
}
