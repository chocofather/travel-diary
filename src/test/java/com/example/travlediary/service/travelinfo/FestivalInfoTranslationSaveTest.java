package com.example.travlediary.service.travelinfo;

import com.example.travlediary.dto.FestivalCreateForm;
import com.example.travlediary.dto.FestivalEditForm;
import com.example.travlediary.dto.FestivalInfoTranslationForm;
import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.model.FestivalInfoTranslation;
import com.example.travlediary.repository.travelinfo.FestivalInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 축제·행사 상세정보 번역 저장 규칙을 본다.
 *
 * <p>한국어는 늘 화면의 행사 상세정보 입력과 같은 값이 되고, 나머지 언어는 여섯 칸 중 하나라도
 * 값이 있으면 남고 전부 비면 그 언어 줄만 사라진다.
 */
@ExtendWith(MockitoExtension.class)
class FestivalInfoTranslationSaveTest {

    @Mock private FestivalInfoMapper festivalInfoMapper;

    private FestivalInfoService service;

    @BeforeEach
    void setUp() {
        service = new FestivalInfoService(festivalInfoMapper);
    }

    @Test
    void bothFestivalFormsStartWithOneEmptySlotPerCanonicalLanguage() {
        assertThat(new FestivalCreateForm().getFestivalInfoTranslations())
                .extracting(FestivalInfoTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        assertThat(new FestivalEditForm().getFestivalInfoTranslations())
                .extracting(FestivalInfoTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        assertThat(new FestivalCreateForm().getFestivalInfoTranslations())
                .allSatisfy(slot -> {
                    assertThat(slot.getEventPlace()).isEmpty();
                    assertThat(slot.getUseTime()).isEmpty();
                    assertThat(slot.getSponsor2()).isEmpty();
                });
    }

    @Test
    void koreanRowIsInsertedFromTheBaseWhenItDoesNotExistYet() {
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of());

        service.saveTranslations(41L, base(), List.of());

        List<FestivalInfoTranslation> inserted = captureInserts();
        assertThat(inserted).hasSize(1);
        assertThat(inserted.get(0).getInfoId()).isEqualTo(41L);
        assertThat(inserted.get(0).getLanguageCode()).isEqualTo("ko");
        assertThat(inserted.get(0).getEventPlace()).isEqualTo("경복궁");
        assertThat(inserted.get(0).getUseTime()).isEqualTo("1인 60,000원");
        assertThat(inserted.get(0).getSponsor2()).isEqualTo("국가유산진흥원");
    }

    @Test
    void koreanRowIsUpdatedToStayInSyncWithTheBase() {
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                stored(1L, "ko", "예전 장소")));

        service.saveTranslations(41L, base(), List.of());

        List<FestivalInfoTranslation> updated = captureUpdates();
        assertThat(updated).hasSize(1);
        assertThat(updated.get(0).getLanguageCode()).isEqualTo("ko");
        assertThat(updated.get(0).getEventPlace()).isEqualTo("경복궁");
        verify(festivalInfoMapper, never()).insertTranslation(any());
    }

    @Test
    void koreanSlotInTheTranslationFormsIsIgnoredSoTheBaseAlwaysWins() {
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of());

        FestivalInfoTranslationForm koreanSlot = new FestivalInfoTranslationForm("ko");
        koreanSlot.setEventPlace("폼이 보낸 장소");

        service.saveTranslations(41L, base(), List.of(koreanSlot));

        List<FestivalInfoTranslation> inserted = captureInserts();
        assertThat(inserted).hasSize(1);
        assertThat(inserted.get(0).getEventPlace()).isEqualTo("경복궁");
    }

    @Test
    void koreanRowIsNotCreatedWhenTheBaseHasNoEventDetailAtAll() {
        // ko 백필이 값 없는 축제를 건너뛴 것과 같은 규칙이다.
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of());
        FestivalInfo emptyBase = new FestivalInfo();
        emptyBase.setInfoId(41L);
        emptyBase.setContactTel("1522-2295");

        service.saveTranslations(41L, emptyBase, List.of());

        verify(festivalInfoMapper, never()).insertTranslation(any());
        verify(festivalInfoMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    @Test
    void koreanRowIsRemovedWhenTheBaseEventDetailIsCleared() {
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                stored(1L, "ko", "경복궁")));
        FestivalInfo emptyBase = new FestivalInfo();
        emptyBase.setInfoId(41L);

        service.saveTranslations(41L, emptyBase, List.of());

        verify(festivalInfoMapper, times(1)).deleteTranslation(41L, "ko");
    }

    @Test
    void newForeignRowIsInsertedAndAnExistingOneIsUpdated() {
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                stored(1L, "ko", "경복궁"),
                stored(2L, "en", "Old place")));

        service.saveTranslations(41L, base(), List.of(english(), japanese()));

        assertThat(captureUpdates())
                .extracting(FestivalInfoTranslation::getLanguageCode,
                        FestivalInfoTranslation::getEventPlace)
                .containsExactly(tuple("ko", "경복궁"), tuple("en", "Gyeongbokgung Palace"));
        assertThat(captureInserts())
                .extracting(FestivalInfoTranslation::getLanguageCode,
                        FestivalInfoTranslation::getEventPlace)
                .containsExactly(tuple("ja", "景福宮"));
    }

    @Test
    void oneFilledFieldIsEnoughToKeepTheLanguageRow() {
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                stored(1L, "ko", "경복궁")));

        FestivalInfoTranslationForm onlyUseTime = new FestivalInfoTranslationForm("en");
        onlyUseTime.setUseTime("  KRW 60,000 per person  ");

        service.saveTranslations(41L, base(), List.of(onlyUseTime));

        FestivalInfoTranslation inserted = captureInserts().get(0);
        assertThat(inserted.getLanguageCode()).isEqualTo("en");
        // 저장 문자열은 앞뒤 공백을 걷어낸다
        assertThat(inserted.getUseTime()).isEqualTo("KRW 60,000 per person");
        assertThat(inserted.getEventPlace()).isNull();
        assertThat(inserted.getSponsor1()).isNull();
    }

    @Test
    void blankLanguageDeletesOnlyThatLanguageRow() {
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                stored(1L, "ko", "경복궁"),
                stored(2L, "en", "Old place"),
                stored(3L, "ja", "景福宮")));

        FestivalInfoTranslationForm blankEnglish = new FestivalInfoTranslationForm(
                "en", "  ", "", null, "   ", "", null);

        service.saveTranslations(41L, base(), List.of(blankEnglish, japanese()));

        verify(festivalInfoMapper, times(1)).deleteTranslation(41L, "en");
        verify(festivalInfoMapper, never()).deleteTranslation(41L, "ja");
        verify(festivalInfoMapper, never()).deleteTranslation(41L, "ko");
        assertThat(captureUpdates())
                .extracting(FestivalInfoTranslation::getLanguageCode)
                .containsExactly("ko", "ja");
    }

    @Test
    void blankLanguageWithoutAnExistingRowDoesNothing() {
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                stored(1L, "ko", "경복궁")));

        service.saveTranslations(41L, base(),
                List.of(new FestivalInfoTranslationForm("en", "", "", "", "", "", "")));

        verify(festivalInfoMapper, never()).insertTranslation(any());
        verify(festivalInfoMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    @Test
    void unsupportedAndLegacyLanguageCodesAreNeverStored() {
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                stored(1L, "ko", "경복궁")));

        service.saveTranslations(41L, base(), List.of(
                new FestivalInfoTranslationForm("zh", "景福宫", "", "", "", "", ""),
                new FestivalInfoTranslationForm("en-US", "Palace", "", "", "", "", ""),
                new FestivalInfoTranslationForm("EN", "Palace", "", "", "", "", ""),
                new FestivalInfoTranslationForm("fr", "Palais", "", "", "", "", ""),
                new FestivalInfoTranslationForm(null, "이름 없음", "", "", "", "", "")));

        verify(festivalInfoMapper, never()).insertTranslation(any());
        assertThat(captureUpdates())
                .extracting(FestivalInfoTranslation::getLanguageCode)
                .containsExactly("ko");
    }

    @Test
    void canonicalChineseCodesAreStoredSeparately() {
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                stored(1L, "ko", "경복궁")));

        service.saveTranslations(41L, base(), List.of(
                new FestivalInfoTranslationForm("zh-CN", "景福宫", "", "", "", "", ""),
                new FestivalInfoTranslationForm("zh-TW", "景福宮", "", "", "", "", "")));

        assertThat(captureInserts())
                .extracting(FestivalInfoTranslation::getLanguageCode)
                .containsExactly("zh-CN", "zh-TW");
    }

    @Test
    void duplicateLanguageSlotsNeverCauseASecondInsert() {
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                stored(1L, "ko", "경복궁")));

        service.saveTranslations(41L, base(), List.of(
                new FestivalInfoTranslationForm("en", "First place", "", "", "", "", ""),
                new FestivalInfoTranslationForm("en", "Second place", "", "", "", "", "")));

        assertThat(captureInserts())
                .extracting(FestivalInfoTranslation::getEventPlace)
                .containsExactly("First place");
        verify(festivalInfoMapper, times(1)).insertTranslation(any());
    }

    @Test
    void existingTranslationsAreReadOnceNoMatterHowManyLanguagesAreSaved() {
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of());

        service.saveTranslations(41L, base(), List.of(
                english(), japanese(),
                new FestivalInfoTranslationForm("zh-CN", "景福宫", "", "", "", "", ""),
                new FestivalInfoTranslationForm("zh-TW", "景福宮", "", "", "", "", "")));

        verify(festivalInfoMapper, times(1)).findTranslationsByInfoId(41L);
        verify(festivalInfoMapper, never()).findTranslationsByInfoIds(any());
    }

    @Test
    void editScreenPreloadsStoredTranslationsAndLeavesMissingLanguagesEmpty() {
        FestivalInfoTranslation storedEnglish = new FestivalInfoTranslation();
        storedEnglish.setId(2L);
        storedEnglish.setInfoId(41L);
        storedEnglish.setLanguageCode("en");
        storedEnglish.setEventPlace("Gyeongbokgung Palace");
        storedEnglish.setUseTime("KRW 60,000 per person");
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                stored(1L, "ko", "경복궁"), storedEnglish));

        List<FestivalInfoTranslationForm> slots = service.getTranslationForms(41L);

        assertThat(slots).extracting(FestivalInfoTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        assertThat(slot(slots, "en").getEventPlace()).isEqualTo("Gyeongbokgung Palace");
        assertThat(slot(slots, "en").getUseTime()).isEqualTo("KRW 60,000 per person");
        // 저장된 값이 없는 칸과 언어는 빈 문자열로 남는다
        assertThat(slot(slots, "en").getAddress()).isEmpty();
        assertThat(slot(slots, "ja").getEventPlace()).isEmpty();
        assertThat(slot(slots, "zh-TW").getSponsor2()).isEmpty();
        verify(festivalInfoMapper, times(1)).findTranslationsByInfoId(41L);
    }

    @Test
    void translationFormsForANewScreenAreAllEmptySlots() {
        assertThat(service.getTranslationForms(null))
                .extracting(FestivalInfoTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        verify(festivalInfoMapper, never()).findTranslationsByInfoId(anyLong());
    }

    @Test
    void missingInfoIdTouchesNothing() {
        service.saveTranslations(null, base(), List.of(english()));

        verify(festivalInfoMapper, never()).findTranslationsByInfoId(anyLong());
        verify(festivalInfoMapper, never()).insertTranslation(any());
        verify(festivalInfoMapper, never()).updateTranslation(any());
        verify(festivalInfoMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    private FestivalInfo base() {
        FestivalInfo info = new FestivalInfo();
        info.setInfoId(41L);
        info.setEventPlace("경복궁");
        info.setAddress("서울특별시 종로구 사직로 161");
        info.setPlayTime("1부 18:20~20:10");
        info.setUseTime("1인 60,000원");
        info.setSponsor1("국가유산청");
        info.setSponsor2("국가유산진흥원");
        info.setContactTel("1522-2295");
        info.setHomepageUrl("https://www.example.com/festival");
        return info;
    }

    private FestivalInfoTranslationForm english() {
        return new FestivalInfoTranslationForm("en", "Gyeongbokgung Palace",
                "161 Sajik-ro, Jongno-gu, Seoul", "Part 1 18:20-20:10",
                "KRW 60,000 per person", "Korea Heritage Service", "Korea Heritage Agency");
    }

    private FestivalInfoTranslationForm japanese() {
        return new FestivalInfoTranslationForm("ja", "景福宮", "", "", "", "", "");
    }

    private FestivalInfoTranslationForm slot(List<FestivalInfoTranslationForm> slots,
                                             String languageCode) {
        return slots.stream()
                .filter(candidate -> languageCode.equals(candidate.getLanguageCode()))
                .findFirst()
                .orElseThrow();
    }

    private FestivalInfoTranslation stored(Long id, String languageCode, String eventPlace) {
        FestivalInfoTranslation translation = new FestivalInfoTranslation();
        translation.setId(id);
        translation.setInfoId(41L);
        translation.setLanguageCode(languageCode);
        translation.setEventPlace(eventPlace);
        return translation;
    }

    private List<FestivalInfoTranslation> captureInserts() {
        ArgumentCaptor<FestivalInfoTranslation> captor =
                ArgumentCaptor.forClass(FestivalInfoTranslation.class);
        verify(festivalInfoMapper, atLeastOnce()).insertTranslation(captor.capture());
        return captor.getAllValues();
    }

    private List<FestivalInfoTranslation> captureUpdates() {
        ArgumentCaptor<FestivalInfoTranslation> captor =
                ArgumentCaptor.forClass(FestivalInfoTranslation.class);
        verify(festivalInfoMapper, atLeastOnce()).updateTranslation(captor.capture());
        return captor.getAllValues();
    }
}
