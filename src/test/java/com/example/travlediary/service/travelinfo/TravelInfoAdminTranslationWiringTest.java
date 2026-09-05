package com.example.travlediary.service.travelinfo;

import com.example.travlediary.dto.InfoPeriodForm;
import com.example.travlediary.dto.TravelInfoForm;
import com.example.travlediary.dto.TravelInfoTranslationForm;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.TravelInfo;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 여행정보 폼과 번역 저장이 이어져 있는지 본다.
 *
 * <p>한국어는 화면의 제목·본문이 그대로 ko 줄이 되고, 외국어는 선택 입력이라
 * 비워 두어도 base 등록이 막히지 않아야 한다.
 */
@ExtendWith(MockitoExtension.class)
class TravelInfoAdminTranslationWiringTest {

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
    void newFormStartsWithOneEmptySlotPerCanonicalLanguage() {
        assertThat(new TravelInfoForm().getTranslations())
                .extracting(TravelInfoTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        assertThat(new TravelInfoForm().getTranslations())
                .allSatisfy(slot -> {
                    assertThat(slot.getTitle()).isEmpty();
                    assertThat(slot.getContent()).isEmpty();
                });
    }

    @Test
    void createStoresForeignTranslationsInTheSameCallAsTheBase() {
        givenCategory(TravelInfoContentType.GENERAL);
        givenGeneratedId(10L);
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of());

        TravelInfoForm form = generalForm();
        setSlot(form, "en", "Spring packing list", "<p>English body</p>");
        setSlot(form, "ja", "春の旅の持ち物", "<p>日本語本文</p>");

        travelInfoService.create(form, 7L);

        assertThat(captureInserts())
                .extracting(TravelInfoTranslation::getLanguageCode,
                        TravelInfoTranslation::getTitle)
                .containsExactly(
                        // ko 는 화면의 제목·본문에서 나온다
                        org.assertj.core.groups.Tuple.tuple("ko", "봄 여행 준비물"),
                        org.assertj.core.groups.Tuple.tuple("en", "Spring packing list"),
                        org.assertj.core.groups.Tuple.tuple("ja", "春の旅の持ち物"));
    }

    @Test
    void updateStoresForeignTranslationsInTheSameCallAsTheBase() {
        givenCategory(TravelInfoContentType.GENERAL);
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(existingInfo(10L));
        when(travelInfoMapper.updateTravelInfo(any())).thenReturn(1);
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                storedTranslation(1L, 10L, "ko", "예전 제목", "<p>예전 본문</p>"),
                storedTranslation(2L, 10L, "en", "Old title", "<p>Old body</p>")));

        TravelInfoForm form = generalForm();
        setSlot(form, "en", "Spring packing list", "<p>English body</p>");

        travelInfoService.update(10L, form);

        assertThat(captureUpdates())
                .extracting(TravelInfoTranslation::getLanguageCode,
                        TravelInfoTranslation::getTitle)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ko", "봄 여행 준비물"),
                        org.assertj.core.groups.Tuple.tuple("en", "Spring packing list"));
    }

    @Test
    void koreanRowFollowsTheBaseEvenWithoutAnyTranslationInput() {
        givenCategory(TravelInfoContentType.GENERAL);
        givenGeneratedId(10L);
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of());

        travelInfoService.create(generalForm(), 7L);

        List<TravelInfoTranslation> inserted = captureInserts();
        assertThat(inserted).hasSize(1);
        assertThat(inserted.get(0).getLanguageCode()).isEqualTo("ko");
        assertThat(inserted.get(0).getTitle()).isEqualTo("봄 여행 준비물");
        assertThat(inserted.get(0).getContent()).isEqualTo("<p>원문 본문</p>");
    }

    @Test
    void emptyForeignSlotsNeverBlockTheBaseSave() {
        givenCategory(TravelInfoContentType.GENERAL);
        givenGeneratedId(10L);
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of());

        // 슬롯은 그대로 비어 있다 (신규 등록 화면에서 아무 것도 입력하지 않은 상태)
        assertThat(travelInfoService.create(generalForm(), 7L)).isEqualTo(10L);

        verify(travelInfoMapper).insertTravelInfo(any());
        verify(travelInfoMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    @Test
    void titleOnlyAndContentOnlyForeignSlotsAreBothAccepted() {
        givenCategory(TravelInfoContentType.GENERAL);
        givenGeneratedId(10L);
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of());

        TravelInfoForm form = generalForm();
        setSlot(form, "en", "Spring packing list", "<p><br></p>");
        setSlot(form, "ja", "", "<p>日本語本文</p>");

        travelInfoService.create(form, 7L);

        assertThat(captureInserts())
                .filteredOn(translation -> !"ko".equals(translation.getLanguageCode()))
                .extracting(TravelInfoTranslation::getLanguageCode,
                        TravelInfoTranslation::getTitle,
                        TravelInfoTranslation::getContent)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("en", "Spring packing list", null),
                        org.assertj.core.groups.Tuple.tuple("ja", null, "<p>日本語本文</p>"));
    }

    @Test
    void editFormPreloadsStoredTranslationsAndLeavesMissingLanguagesEmpty() {
        TravelInfo existing = existingInfo(10L);
        when(travelInfoMapper.findById(10L)).thenReturn(existing);
        when(travelInfoMapper.findPeriodsByInfoId(10L)).thenReturn(List.of());
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                storedTranslation(1L, 10L, "ko", "봄 여행 준비물", "<p>원문 본문</p>"),
                storedTranslation(2L, 10L, "en", "Spring packing list",
                        "<p><img src=\"/uploads/editor/en.png\"></p>")));

        TravelInfoForm form = travelInfoService.getForm(10L);

        assertThat(form.getTranslations())
                .extracting(TravelInfoTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        TravelInfoTranslationForm english = slot(form, "en");
        assertThat(english.getTitle()).isEqualTo("Spring packing list");
        // Quill 이미지 HTML 도 그대로 실려 온다
        assertThat(english.getContent()).isEqualTo("<p><img src=\"/uploads/editor/en.png\"></p>");
        // 저장된 줄이 없는 언어는 빈 슬롯으로 남는다
        assertThat(slot(form, "ja").getTitle()).isEmpty();
        assertThat(slot(form, "ja").getContent()).isEmpty();
        assertThat(slot(form, "zh-TW").getContent()).isEmpty();
        // 한국어 base 로딩 방식은 그대로다
        assertThat(form.getTitle()).isEqualTo("봄 여행 준비물");
        assertThat(form.getContent()).isEqualTo("<p>원문 본문</p>");
    }

    @Test
    void translationFormsAreReadOnceWhenTheEditScreenOpens() {
        when(travelInfoMapper.findById(10L)).thenReturn(existingInfo(10L));
        when(travelInfoMapper.findPeriodsByInfoId(10L)).thenReturn(List.of());
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of());

        travelInfoService.getForm(10L);

        verify(travelInfoMapper, times(1)).findTranslationsByInfoId(10L);
    }

    @Test
    void translationFormsForANewScreenAreAllEmptySlots() {
        assertThat(travelInfoService.getTranslationForms(null))
                .extracting(TravelInfoTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        verify(travelInfoMapper, never()).findTranslationsByInfoId(anyLong());
    }

    private TravelInfoForm generalForm() {
        TravelInfoForm form = new TravelInfoForm();
        form.setTitle("봄 여행 준비물");
        form.setContent("<p>원문 본문</p>");
        form.setScope(TravelInfoScope.DOMESTIC);
        form.setContentType(TravelInfoContentType.GENERAL);
        form.setCategoryId(3L);
        form.setPeriods(List.<InfoPeriodForm>of());
        return form;
    }

    private void setSlot(TravelInfoForm form, String languageCode, String title, String content) {
        TravelInfoTranslationForm target = slot(form, languageCode);
        target.setTitle(title);
        target.setContent(content);
    }

    private TravelInfoTranslationForm slot(TravelInfoForm form, String languageCode) {
        return form.getTranslations().stream()
                .filter(slot -> languageCode.equals(slot.getLanguageCode()))
                .findFirst()
                .orElseThrow();
    }

    private void givenCategory(TravelInfoContentType contentType) {
        InfoCategory category = new InfoCategory();
        category.setId(3L);
        category.setName("계절여행");
        category.setContentType(contentType);
        when(infoCategoryMapper.findById(3L)).thenReturn(category);
    }

    private void givenGeneratedId(Long id) {
        doAnswer(invocation -> {
            invocation.getArgument(0, TravelInfo.class).setId(id);
            return 1;
        }).when(travelInfoMapper).insertTravelInfo(any());
    }

    private TravelInfo existingInfo(Long id) {
        TravelInfo info = new TravelInfo();
        info.setId(id);
        info.setTitle("봄 여행 준비물");
        info.setContent("<p>원문 본문</p>");
        info.setScope(TravelInfoScope.DOMESTIC);
        info.setContentType(TravelInfoContentType.GENERAL);
        info.setCategoryId(3L);
        return info;
    }

    private TravelInfoTranslation storedTranslation(Long id, Long travelInfoId, String languageCode,
                                                    String title, String content) {
        TravelInfoTranslation translation = new TravelInfoTranslation();
        translation.setId(id);
        translation.setTravelInfoId(travelInfoId);
        translation.setLanguageCode(languageCode);
        translation.setTitle(title);
        translation.setContent(content);
        return translation;
    }

    private List<TravelInfoTranslation> captureInserts() {
        ArgumentCaptor<TravelInfoTranslation> captor =
                ArgumentCaptor.forClass(TravelInfoTranslation.class);
        verify(travelInfoMapper, org.mockito.Mockito.atLeastOnce())
                .insertTranslation(captor.capture());
        return captor.getAllValues();
    }

    private List<TravelInfoTranslation> captureUpdates() {
        ArgumentCaptor<TravelInfoTranslation> captor =
                ArgumentCaptor.forClass(TravelInfoTranslation.class);
        verify(travelInfoMapper, org.mockito.Mockito.atLeastOnce())
                .updateTranslation(captor.capture());
        return captor.getAllValues();
    }
}
