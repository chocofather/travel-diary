package com.example.travlediary.service.travelinfo;

import com.example.travlediary.dto.TravelInfoTranslationForm;
import com.example.travlediary.model.TravelInfoTranslation;
import com.example.travlediary.repository.bookmark.BookmarkMapper;
import com.example.travlediary.repository.category.InfoCategoryMapper;
import com.example.travlediary.repository.travelinfo.TravelInfoMapper;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.repository.category.CategoryMapper;
import com.example.travlediary.repository.category.CountryCategoryMapper;
import com.example.travlediary.service.category.LocalizedReferenceNameResolver;
import com.example.travlediary.service.category.ReferenceNameLocalizationService;
import com.example.travlediary.service.post.PostContentSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 번역 저장 규칙을 본다.
 *
 * <p>한국어는 늘 base 와 같은 값이 되고, 나머지 언어는 값이 있으면 남고 비면 그 언어 줄만 사라진다.
 */
@ExtendWith(MockitoExtension.class)
class TravelInfoTranslationSaveTest {

    private static final String BASE_TITLE = "봄 여행 준비물";
    private static final String BASE_CONTENT = "<p>원문 본문</p>";

    @Mock private TravelInfoMapper travelInfoMapper;
    @Mock private BookmarkMapper bookmarkMapper;
    @Mock private InfoCategoryMapper infoCategoryMapper;
    @Mock private FileUploadService fileUploadService;

    private TravelInfoService travelInfoService;

    @BeforeEach
    void setUp() {
        travelInfoService = new TravelInfoService(
                travelInfoMapper, bookmarkMapper, infoCategoryMapper,
                new PostContentSanitizer(), fileUploadService,
                new TravelInfoLocalizationService(travelInfoMapper),
                new ReferenceNameLocalizationService(
                        org.mockito.Mockito.mock(CountryCategoryMapper.class),
                        org.mockito.Mockito.mock(CategoryMapper.class),
                        infoCategoryMapper, new LocalizedReferenceNameResolver()));
    }

    @Test
    void koreanRowIsInsertedFromBaseWhenItDoesNotExistYet() {
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of());

        travelInfoService.saveTranslations(10L, BASE_TITLE, BASE_CONTENT, List.of());

        TravelInfoTranslation inserted = captureInsert();
        assertThat(inserted.getTravelInfoId()).isEqualTo(10L);
        assertThat(inserted.getLanguageCode()).isEqualTo("ko");
        assertThat(inserted.getTitle()).isEqualTo(BASE_TITLE);
        assertThat(inserted.getContent()).isEqualTo(BASE_CONTENT);
        verify(travelInfoMapper, never()).updateTranslation(any());
        verify(travelInfoMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    @Test
    void koreanRowIsUpdatedToStayInSyncWithTheBase() {
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                stored(1L, 10L, "ko", "예전 제목", "<p>예전 본문</p>")));

        travelInfoService.saveTranslations(10L, BASE_TITLE, BASE_CONTENT, List.of());

        TravelInfoTranslation updated = captureUpdate();
        assertThat(updated.getLanguageCode()).isEqualTo("ko");
        assertThat(updated.getTitle()).isEqualTo(BASE_TITLE);
        assertThat(updated.getContent()).isEqualTo(BASE_CONTENT);
        verify(travelInfoMapper, never()).insertTranslation(any());
    }

    @Test
    void koreanSlotInTheTranslationFormsIsIgnoredSoTheBaseAlwaysWins() {
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of());

        travelInfoService.saveTranslations(10L, BASE_TITLE, BASE_CONTENT, List.of(
                new TravelInfoTranslationForm("ko", "폼이 보낸 제목", "<p>폼이 보낸 본문</p>")));

        TravelInfoTranslation inserted = captureInsert();
        assertThat(inserted.getLanguageCode()).isEqualTo("ko");
        assertThat(inserted.getTitle()).isEqualTo(BASE_TITLE);
        assertThat(inserted.getContent()).isEqualTo(BASE_CONTENT);
        verify(travelInfoMapper, times(1)).insertTranslation(any());
    }

    @Test
    void newForeignLanguageRowIsInserted() {
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                stored(1L, 10L, "ko", BASE_TITLE, BASE_CONTENT)));

        travelInfoService.saveTranslations(10L, BASE_TITLE, BASE_CONTENT, List.of(
                new TravelInfoTranslationForm("en", "Spring packing list", "<p>English body</p>")));

        TravelInfoTranslation inserted = captureInsert();
        assertThat(inserted.getLanguageCode()).isEqualTo("en");
        assertThat(inserted.getTitle()).isEqualTo("Spring packing list");
        assertThat(inserted.getContent()).isEqualTo("<p>English body</p>");
    }

    @Test
    void existingForeignLanguageRowIsUpdated() {
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                stored(1L, 10L, "ko", BASE_TITLE, BASE_CONTENT),
                stored(2L, 10L, "en", "Old title", "<p>Old body</p>")));

        travelInfoService.saveTranslations(10L, BASE_TITLE, BASE_CONTENT, List.of(
                new TravelInfoTranslationForm("en", "Spring packing list", "<p>English body</p>")));

        assertThat(captureUpdates())
                .extracting(TravelInfoTranslation::getLanguageCode)
                .containsExactly("ko", "en");
        verify(travelInfoMapper, never()).insertTranslation(any());
        verify(travelInfoMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    @Test
    void titleOnlyTranslationIsStored() {
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                stored(1L, 10L, "ko", BASE_TITLE, BASE_CONTENT)));

        travelInfoService.saveTranslations(10L, BASE_TITLE, BASE_CONTENT, List.of(
                new TravelInfoTranslationForm("ja", "  春の旅の持ち物  ", "   ")));

        TravelInfoTranslation inserted = captureInsert();
        assertThat(inserted.getLanguageCode()).isEqualTo("ja");
        assertThat(inserted.getTitle()).isEqualTo("春の旅の持ち物");
        assertThat(inserted.getContent()).isNull();
    }

    @Test
    void contentOnlyTranslationIsStored() {
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                stored(1L, 10L, "ko", BASE_TITLE, BASE_CONTENT)));

        travelInfoService.saveTranslations(10L, BASE_TITLE, BASE_CONTENT, List.of(
                new TravelInfoTranslationForm("ja", "   ", "<p>日本語本文</p>")));

        TravelInfoTranslation inserted = captureInsert();
        assertThat(inserted.getLanguageCode()).isEqualTo("ja");
        assertThat(inserted.getTitle()).isNull();
        assertThat(inserted.getContent()).isEqualTo("<p>日本語本文</p>");
    }

    @Test
    void blankTranslationDeletesTheExistingRowForThatLanguageOnly() {
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                stored(1L, 10L, "ko", BASE_TITLE, BASE_CONTENT),
                stored(2L, 10L, "en", "Old title", "<p>Old body</p>"),
                stored(3L, 10L, "ja", "古いタイトル", "<p>古い本文</p>")));

        travelInfoService.saveTranslations(10L, BASE_TITLE, BASE_CONTENT, List.of(
                new TravelInfoTranslationForm("en", "  ", "<p><br></p>"),
                new TravelInfoTranslationForm("ja", "春の旅の持ち物", "<p>日本語本文</p>")));

        verify(travelInfoMapper, times(1)).deleteTranslation(10L, "en");
        verify(travelInfoMapper, never()).deleteTranslation(10L, "ja");
        verify(travelInfoMapper, never()).deleteTranslation(10L, "ko");
        assertThat(captureUpdates())
                .extracting(TravelInfoTranslation::getLanguageCode)
                .containsExactly("ko", "ja");
    }

    @Test
    void blankTranslationWithoutAnExistingRowDoesNothing() {
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                stored(1L, 10L, "ko", BASE_TITLE, BASE_CONTENT)));

        travelInfoService.saveTranslations(10L, BASE_TITLE, BASE_CONTENT, List.of(
                new TravelInfoTranslationForm("en", "   ", "   ")));

        verify(travelInfoMapper, never()).insertTranslation(any());
        verify(travelInfoMapper, never()).deleteTranslation(anyLong(), anyString());
        assertThat(captureUpdates())
                .extracting(TravelInfoTranslation::getLanguageCode)
                .containsExactly("ko");
    }

    @Test
    void emptyQuillHtmlCountsAsNoContent() {
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                stored(1L, 10L, "ko", BASE_TITLE, BASE_CONTENT)));

        travelInfoService.saveTranslations(10L, BASE_TITLE, BASE_CONTENT, List.of(
                new TravelInfoTranslationForm("en", "Spring packing list",
                        "<p><br></p><p>   </p>")));

        TravelInfoTranslation inserted = captureInsert();
        assertThat(inserted.getTitle()).isEqualTo("Spring packing list");
        assertThat(inserted.getContent()).isNull();
    }

    @Test
    void contentWithOnlyAnImageIsStored() {
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                stored(1L, 10L, "ko", BASE_TITLE, BASE_CONTENT)));

        travelInfoService.saveTranslations(10L, BASE_TITLE, BASE_CONTENT, List.of(
                new TravelInfoTranslationForm("en", "   ",
                        "<p><img src=\"/uploads/editor/en-infographic.png\"></p>")));

        TravelInfoTranslation inserted = captureInsert();
        assertThat(inserted.getLanguageCode()).isEqualTo("en");
        assertThat(inserted.getContent())
                .contains("<img src=\"/uploads/editor/en-infographic.png\"");
    }

    @Test
    void translatedContentGoesThroughTheSanitizerBeforeItIsStored() {
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                stored(1L, 10L, "ko", BASE_TITLE, BASE_CONTENT)));

        travelInfoService.saveTranslations(10L, BASE_TITLE, BASE_CONTENT, List.of(
                new TravelInfoTranslationForm("en", "Spring packing list",
                        "<p onclick=\"alert(1)\">English body</p><script>alert(1)</script>")));

        assertThat(captureInsert().getContent()).isEqualTo("<p>English body</p>");
    }

    @Test
    void unsupportedAndLegacyLanguageCodesAreNeverStored() {
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                stored(1L, 10L, "ko", BASE_TITLE, BASE_CONTENT)));

        travelInfoService.saveTranslations(10L, BASE_TITLE, BASE_CONTENT, List.of(
                new TravelInfoTranslationForm("zh", "简体标题", "<p>简体正文</p>"),
                new TravelInfoTranslationForm("en-US", "American title", "<p>American body</p>"),
                new TravelInfoTranslationForm("EN", "Upper case title", "<p>Upper case body</p>"),
                new TravelInfoTranslationForm("fr", "Titre", "<p>Corps</p>"),
                new TravelInfoTranslationForm(null, "제목 없음", "<p>본문</p>")));

        verify(travelInfoMapper, never()).insertTranslation(any());
        verify(travelInfoMapper, never()).deleteTranslation(anyLong(), anyString());
        assertThat(captureUpdates())
                .extracting(TravelInfoTranslation::getLanguageCode)
                .containsExactly("ko");
    }

    @Test
    void canonicalChineseCodesAreStoredSeparately() {
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                stored(1L, 10L, "ko", BASE_TITLE, BASE_CONTENT)));

        travelInfoService.saveTranslations(10L, BASE_TITLE, BASE_CONTENT, List.of(
                new TravelInfoTranslationForm("zh-CN", "简体标题", "<p>简体正文</p>"),
                new TravelInfoTranslationForm("zh-TW", "繁體標題", "<p>繁體正文</p>")));

        assertThat(captureInserts())
                .extracting(TravelInfoTranslation::getLanguageCode)
                .containsExactly("zh-CN", "zh-TW");
    }

    @Test
    void duplicateLanguageSlotsNeverCauseASecondInsert() {
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                stored(1L, 10L, "ko", BASE_TITLE, BASE_CONTENT)));

        travelInfoService.saveTranslations(10L, BASE_TITLE, BASE_CONTENT, List.of(
                new TravelInfoTranslationForm("en", "First title", "<p>First body</p>"),
                new TravelInfoTranslationForm("en", "Second title", "<p>Second body</p>")));

        // 같은 언어가 두 번 오면 앞의 값만 쓴다.
        assertThat(captureInserts())
                .extracting(TravelInfoTranslation::getTitle)
                .containsExactly("First title");
        verify(travelInfoMapper, times(1)).insertTranslation(any());
    }

    @Test
    void existingTranslationsAreReadOnceNoMatterHowManyLanguagesAreSaved() {
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                stored(1L, 10L, "ko", BASE_TITLE, BASE_CONTENT),
                stored(2L, 10L, "en", "Old title", "<p>Old body</p>")));

        travelInfoService.saveTranslations(10L, BASE_TITLE, BASE_CONTENT, List.of(
                new TravelInfoTranslationForm("en", "Spring packing list", "<p>English body</p>"),
                new TravelInfoTranslationForm("ja", "春の旅の持ち物", "<p>日本語本文</p>"),
                new TravelInfoTranslationForm("zh-CN", "简体标题", "<p>简体正文</p>"),
                new TravelInfoTranslationForm("zh-TW", "繁體標題", "<p>繁體正文</p>")));

        verify(travelInfoMapper, times(1)).findTranslationsByInfoId(10L);
        verify(travelInfoMapper, never()).findTranslationsByInfoIds(any());
    }

    @Test
    void missingTravelInfoIdTouchesNothing() {
        travelInfoService.saveTranslations(null, BASE_TITLE, BASE_CONTENT, List.of(
                new TravelInfoTranslationForm("en", "Spring packing list", "<p>English body</p>")));

        verify(travelInfoMapper, never()).findTranslationsByInfoId(anyLong());
        verify(travelInfoMapper, never()).insertTranslation(any());
        verify(travelInfoMapper, never()).updateTranslation(any());
        verify(travelInfoMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    @Test
    void nullTranslationFormsStillSyncTheKoreanRow() {
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of());

        travelInfoService.saveTranslations(10L, BASE_TITLE, BASE_CONTENT, null);

        assertThat(captureInsert().getLanguageCode()).isEqualTo("ko");
    }

    private TravelInfoTranslation captureInsert() {
        List<TravelInfoTranslation> inserted = captureInserts();
        assertThat(inserted).hasSize(1);
        return inserted.get(0);
    }

    private List<TravelInfoTranslation> captureInserts() {
        ArgumentCaptor<TravelInfoTranslation> captor =
                ArgumentCaptor.forClass(TravelInfoTranslation.class);
        verify(travelInfoMapper, org.mockito.Mockito.atLeastOnce()).insertTranslation(captor.capture());
        return captor.getAllValues();
    }

    private TravelInfoTranslation captureUpdate() {
        List<TravelInfoTranslation> updated = captureUpdates();
        assertThat(updated).hasSize(1);
        return updated.get(0);
    }

    private List<TravelInfoTranslation> captureUpdates() {
        ArgumentCaptor<TravelInfoTranslation> captor =
                ArgumentCaptor.forClass(TravelInfoTranslation.class);
        verify(travelInfoMapper, org.mockito.Mockito.atLeastOnce()).updateTranslation(captor.capture());
        return captor.getAllValues();
    }

    private TravelInfoTranslation stored(Long id, Long travelInfoId, String languageCode,
                                         String title, String content) {
        TravelInfoTranslation translation = new TravelInfoTranslation();
        translation.setId(id);
        translation.setTravelInfoId(travelInfoId);
        translation.setLanguageCode(languageCode);
        translation.setTitle(title);
        translation.setContent(content);
        return translation;
    }
}
