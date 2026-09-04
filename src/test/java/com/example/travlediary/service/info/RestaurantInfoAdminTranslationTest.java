package com.example.travlediary.service.info;

import com.example.travlediary.dto.RestaurantInfoTranslationForm;
import com.example.travlediary.model.RestaurantInfo;
import com.example.travlediary.model.RestaurantInfoTranslation;
import com.example.travlediary.repository.info.RestaurantInfoMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 식당 상세정보 저장: 한국어는 원본이자 ko 번역, 나머지 네 언어는 슬롯 값 그대로.
 *
 * <p>언어 한 줄이 단위이며 legacy 'zh' 는 쓰지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class RestaurantInfoAdminTranslationTest {

    @Mock private RestaurantInfoMapper restaurantInfoMapper;
    @InjectMocks private RestaurantInfoService restaurantInfoService;

    private RestaurantInfo koreanBase;

    @BeforeEach
    void setUpBase() {
        koreanBase = new RestaurantInfo();
        koreanBase.setDestinationId(21L);
        koreanBase.setMainMenu("비빔밥");
        koreanBase.setPriceRange("1만원~2만원");
        koreanBase.setOpeningHours("11:00~21:00");
        koreanBase.setBreakTime("15:00~17:00");
        koreanBase.setClosedDays("매주 월요일");
        koreanBase.setEtc("단체 예약 가능");
        // 번역 대상이 아닌 값들
        koreanBase.setContactNumber("02-1234-5678");
        koreanBase.setHomepageUrl("https://example.com");
        koreanBase.setSeatCount(48);
        koreanBase.setParkingAvailable(true);
    }

    @Test
    void newRestaurantStoresKoreanFromTheBaseAndTheFourOtherLanguages() {
        when(restaurantInfoMapper.findTranslationsByDestinationId(21L)).thenReturn(List.of());

        restaurantInfoService.saveTranslations(21L, koreanBase, filledSlots());

        ArgumentCaptor<RestaurantInfoTranslation> captor =
                ArgumentCaptor.forClass(RestaurantInfoTranslation.class);
        verify(restaurantInfoMapper, times(5)).insertTranslation(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(RestaurantInfoTranslation::getLanguageCode,
                        RestaurantInfoTranslation::getMainMenu)
                .containsExactly(
                        tuple("ko", "비빔밥"),
                        tuple("en", "Bibimbap"),
                        tuple("ja", "ビビンバ"),
                        tuple("zh-CN", "拌饭"),
                        tuple("zh-TW", "拌飯"));
        assertThat(captor.getAllValues())
                .extracting(RestaurantInfoTranslation::getLanguageCode)
                .doesNotContain("zh");
        // 한국어 줄은 원본 여섯 칸을 그대로 옮긴다
        RestaurantInfoTranslation korean = captor.getAllValues().get(0);
        assertThat(korean.getPriceRange()).isEqualTo("1만원~2만원");
        assertThat(korean.getOpeningHours()).isEqualTo("11:00~21:00");
        assertThat(korean.getBreakTime()).isEqualTo("15:00~17:00");
        assertThat(korean.getClosedDays()).isEqualTo("매주 월요일");
        assertThat(korean.getEtc()).isEqualTo("단체 예약 가능");
        verify(restaurantInfoMapper, never()).updateTranslation(any());
        verify(restaurantInfoMapper, never()).deleteTranslation(any(), any());
    }

    @Test
    void existingLanguagesAreUpdatedIndependently() {
        when(restaurantInfoMapper.findTranslationsByDestinationId(21L)).thenReturn(List.of(
                stored("ko", "비빔밥"), stored("en", "Bibimbap"), stored("zh-CN", "拌饭")));

        List<RestaurantInfoTranslationForm> slots = filledSlots();
        slots.get(0).setMainMenu("Signature bibimbap");   // en (기존 줄)

        restaurantInfoService.saveTranslations(21L, koreanBase, slots);

        ArgumentCaptor<RestaurantInfoTranslation> updated =
                ArgumentCaptor.forClass(RestaurantInfoTranslation.class);
        verify(restaurantInfoMapper, times(3)).updateTranslation(updated.capture());
        assertThat(updated.getAllValues())
                .extracting(RestaurantInfoTranslation::getLanguageCode,
                        RestaurantInfoTranslation::getMainMenu)
                .containsExactly(
                        tuple("ko", "비빔밥"),
                        tuple("en", "Signature bibimbap"),
                        tuple("zh-CN", "拌饭"));
        // 저장된 줄이 없던 언어만 새로 넣는다
        ArgumentCaptor<RestaurantInfoTranslation> inserted =
                ArgumentCaptor.forClass(RestaurantInfoTranslation.class);
        verify(restaurantInfoMapper, times(2)).insertTranslation(inserted.capture());
        assertThat(inserted.getAllValues())
                .extracting(RestaurantInfoTranslation::getLanguageCode)
                .containsExactly("ja", "zh-TW");
        verify(restaurantInfoMapper, never()).deleteTranslation(any(), any());
    }

    @Test
    void clearingOneLanguageDeletesOnlyThatRow() {
        when(restaurantInfoMapper.findTranslationsByDestinationId(21L)).thenReturn(List.of(
                stored("ko", "비빔밥"), stored("en", "Bibimbap"), stored("ja", "ビビンバ")));

        List<RestaurantInfoTranslationForm> slots = filledSlots();
        RestaurantInfoTranslationForm japanese = slots.get(1);
        japanese.setMainMenu("   ");
        japanese.setPriceRange(null);
        japanese.setOpeningHours("");
        japanese.setBreakTime(null);
        japanese.setClosedDays(null);
        japanese.setEtc(null);

        restaurantInfoService.saveTranslations(21L, koreanBase, slots);

        verify(restaurantInfoMapper).deleteTranslation(21L, "ja");
        verify(restaurantInfoMapper, never()).deleteTranslation(21L, "en");
        verify(restaurantInfoMapper, never()).deleteTranslation(21L, "ko");
        verify(restaurantInfoMapper, never()).deleteTranslation(eq(21L), eq("zh-CN"));
        // 다른 언어는 예전처럼 저장된다
        verify(restaurantInfoMapper, times(2)).updateTranslation(any());  // ko, en
        verify(restaurantInfoMapper, times(2)).insertTranslation(any());  // zh-CN, zh-TW
    }

    @Test
    void anEmptyLanguageWithoutAStoredRowIsLeftAlone() {
        when(restaurantInfoMapper.findTranslationsByDestinationId(21L)).thenReturn(List.of());

        restaurantInfoService.saveTranslations(21L, koreanBase, emptySlots());

        // 한국어만 저장된다
        verify(restaurantInfoMapper, times(1)).insertTranslation(any());
        verify(restaurantInfoMapper, never()).deleteTranslation(any(), any());
    }

    @Test
    void anEmptyKoreanBaseRemovesTheKoreanRowOnly() {
        RestaurantInfo emptyBase = new RestaurantInfo();
        emptyBase.setDestinationId(21L);
        emptyBase.setContactNumber("02-1234-5678");
        when(restaurantInfoMapper.findTranslationsByDestinationId(21L))
                .thenReturn(List.of(stored("ko", "비빔밥"), stored("en", "Bibimbap")));

        restaurantInfoService.saveTranslations(21L, emptyBase, filledSlots());

        verify(restaurantInfoMapper).deleteTranslation(21L, "ko");
        verify(restaurantInfoMapper, never()).deleteTranslation(21L, "en");
    }

    @Test
    void unknownLanguageSlotsAreIgnored() {
        when(restaurantInfoMapper.findTranslationsByDestinationId(21L)).thenReturn(List.of());
        RestaurantInfoTranslationForm legacy = new RestaurantInfoTranslationForm("zh");
        legacy.setMainMenu("拌饭");

        restaurantInfoService.saveTranslations(21L, koreanBase, List.of(legacy));

        ArgumentCaptor<RestaurantInfoTranslation> captor =
                ArgumentCaptor.forClass(RestaurantInfoTranslation.class);
        verify(restaurantInfoMapper, times(1)).insertTranslation(captor.capture());
        assertThat(captor.getValue().getLanguageCode()).isEqualTo("ko");
    }

    @Test
    void editFormRestoresStoredLanguagesIntoTheirOwnSlots() {
        RestaurantInfoTranslation english = stored("en", "Bibimbap");
        english.setPriceRange("KRW 10,000-20,000");
        english.setEtc("Group reservations available");
        when(restaurantInfoMapper.findTranslationsByDestinationId(21L))
                .thenReturn(List.of(stored("ko", "비빔밥"), english, stored("zh-TW", "拌飯")));

        List<RestaurantInfoTranslationForm> slots =
                restaurantInfoService.getTranslationForms(21L);

        assertThat(slots).extracting(RestaurantInfoTranslationForm::getLanguageCode)
                .containsExactly("en", "ja", "zh-CN", "zh-TW");
        assertThat(slots.get(0).getMainMenu()).isEqualTo("Bibimbap");
        assertThat(slots.get(0).getPriceRange()).isEqualTo("KRW 10,000-20,000");
        assertThat(slots.get(0).getEtc()).isEqualTo("Group reservations available");
        // 저장된 줄이 없는 언어는 빈 슬롯이다
        assertThat(slots.get(1).getMainMenu()).isNull();
        assertThat(slots.get(2).getMainMenu()).isNull();
        assertThat(slots.get(3).getMainMenu()).isEqualTo("拌飯");
    }

    private List<RestaurantInfoTranslationForm> filledSlots() {
        List<RestaurantInfoTranslationForm> slots = new ArrayList<>();
        slots.add(slot("en", "Bibimbap"));
        slots.add(slot("ja", "ビビンバ"));
        slots.add(slot("zh-CN", "拌饭"));
        slots.add(slot("zh-TW", "拌飯"));
        return slots;
    }

    private List<RestaurantInfoTranslationForm> emptySlots() {
        List<RestaurantInfoTranslationForm> slots = new ArrayList<>();
        for (String languageCode : List.of("en", "ja", "zh-CN", "zh-TW")) {
            slots.add(new RestaurantInfoTranslationForm(languageCode));
        }
        return slots;
    }

    private RestaurantInfoTranslationForm slot(String languageCode, String mainMenu) {
        RestaurantInfoTranslationForm slot = new RestaurantInfoTranslationForm(languageCode);
        slot.setMainMenu(mainMenu);
        return slot;
    }

    private RestaurantInfoTranslation stored(String languageCode, String mainMenu) {
        RestaurantInfoTranslation translation = new RestaurantInfoTranslation();
        translation.setDestinationId(21L);
        translation.setLanguageCode(languageCode);
        translation.setMainMenu(mainMenu);
        return translation;
    }
}
