package com.example.travlediary.service.travelinfo;

import com.example.travlediary.dto.FestivalCreateForm;
import com.example.travlediary.dto.FestivalEditData;
import com.example.travlediary.dto.FestivalEditForm;
import com.example.travlediary.dto.FestivalInfoTranslationForm;
import com.example.travlediary.dto.TravelInfoTranslationForm;
import com.example.travlediary.model.FestivalInfo;
import com.example.travlediary.model.FestivalInfoTranslation;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.InfoPeriod;
import com.example.travlediary.model.TravelInfo;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.model.TravelInfoTranslation;
import com.example.travlediary.repository.bookmark.BookmarkMapper;
import com.example.travlediary.repository.category.InfoCategoryMapper;
import com.example.travlediary.repository.travelinfo.FestivalInfoMapper;
import com.example.travlediary.repository.travelinfo.TravelInfoMapper;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.kto.KtoFestivalImageDownloadService;
import com.example.travlediary.service.kto.KtoFestivalService;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 축제·행사 관리자 폼이 travel_info 번역까지 저장하는지 본다.
 *
 * <p>번역 대상은 travel_info 의 제목·본문뿐이다. 행사 상세정보(장소·주소·주최 등)와
 * 기간·썸네일·TourAPI 처리는 그대로 남아야 한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FestivalAdminTranslationWiringTest {

    private static final String BASE_TITLE = "벚꽃 축제";
    private static final String BASE_CONTENT = "<p>행사 소개</p>";

    @Mock private TravelInfoMapper travelInfoMapper;
    @Mock private FestivalInfoMapper festivalInfoMapper;
    @Mock private InfoCategoryMapper infoCategoryMapper;
    @Mock private BookmarkMapper bookmarkMapper;
    @Mock private KtoFestivalService ktoFestivalService;
    @Mock private KtoFestivalImageDownloadService festivalImageDownloadService;
    @Mock private FileUploadService fileUploadService;

    private TravelInfoService travelInfoService;
    private FestivalRegistrationService registrationService;
    private FestivalAdminService adminService;

    @BeforeEach
    void setUp() {
        PostContentSanitizer sanitizer = new PostContentSanitizer();
        travelInfoService = new TravelInfoService(
                travelInfoMapper, bookmarkMapper, infoCategoryMapper, sanitizer, fileUploadService,
                new TravelInfoLocalizationService(travelInfoMapper),
                new ReferenceNameLocalizationService(
                        org.mockito.Mockito.mock(CountryCategoryMapper.class),
                        org.mockito.Mockito.mock(CategoryMapper.class),
                        infoCategoryMapper, new LocalizedReferenceNameResolver()));
        FestivalInfoService festivalInfoService = new FestivalInfoService(festivalInfoMapper);
        registrationService = new FestivalRegistrationService(
                travelInfoMapper, festivalInfoMapper, infoCategoryMapper, sanitizer,
                ktoFestivalService, festivalImageDownloadService, travelInfoService,
                festivalInfoService);
        adminService = new FestivalAdminService(
                travelInfoMapper, festivalInfoMapper, infoCategoryMapper, bookmarkMapper,
                sanitizer, festivalImageDownloadService, travelInfoService, festivalInfoService);
    }

    @Test
    void newFestivalFormStartsWithOneEmptySlotPerCanonicalLanguage() {
        assertThat(new FestivalCreateForm().getTranslations())
                .extracting(TravelInfoTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        assertThat(new FestivalEditForm().getTranslations())
                .extracting(TravelInfoTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        assertThat(new FestivalCreateForm().getTranslations())
                .allSatisfy(slot -> {
                    assertThat(slot.getTitle()).isEmpty();
                    assertThat(slot.getContent()).isEmpty();
                });
    }

    @Test
    void registrationStoresForeignTranslationsWithTheBase() {
        givenFestivalCategory();
        givenGeneratedId(41L);
        when(travelInfoMapper.insertPeriod(any())).thenReturn(1);
        when(festivalInfoMapper.insert(any())).thenReturn(1);
        when(travelInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of());

        FestivalCreateForm form = createForm();
        setSlot(form.getTranslations(), "en", "Cherry Blossom Festival", "<p>English body</p>");
        setSlot(form.getTranslations(), "zh-TW", "櫻花慶典", "<p>繁體正文</p>");

        registrationService.create(form, 7L);

        assertThat(captureInserts())
                .extracting(TravelInfoTranslation::getLanguageCode,
                        TravelInfoTranslation::getTitle)
                .containsExactly(
                        // ko 는 화면의 제목·행사 소개에서 나온다
                        tuple("ko", BASE_TITLE),
                        tuple("en", "Cherry Blossom Festival"),
                        tuple("zh-TW", "櫻花慶典"));
    }

    @Test
    void registrationSyncsKoreanEvenWithoutAnyTranslationInput() {
        givenFestivalCategory();
        givenGeneratedId(41L);
        when(travelInfoMapper.insertPeriod(any())).thenReturn(1);
        when(festivalInfoMapper.insert(any())).thenReturn(1);
        when(travelInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of());

        registrationService.create(createForm(), 7L);

        List<TravelInfoTranslation> inserted = captureInserts();
        assertThat(inserted).hasSize(1);
        assertThat(inserted.get(0).getLanguageCode()).isEqualTo("ko");
        assertThat(inserted.get(0).getTitle()).isEqualTo(BASE_TITLE);
        assertThat(inserted.get(0).getContent()).isEqualTo(BASE_CONTENT);
    }

    @Test
    void registrationKeepsStoringPeriodAndFestivalInfoAlongsideTranslations() {
        givenFestivalCategory();
        givenGeneratedId(41L);
        when(travelInfoMapper.insertPeriod(any())).thenReturn(1);
        when(festivalInfoMapper.insert(any())).thenReturn(1);
        when(travelInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of());

        FestivalCreateForm form = createForm();
        setSlot(form.getTranslations(), "en", "Cherry Blossom Festival", "<p>English body</p>");

        registrationService.create(form, 7L);

        verify(travelInfoMapper).insertTravelInfo(any());
        verify(travelInfoMapper).insertPeriod(any());
        ArgumentCaptor<FestivalInfo> captor = ArgumentCaptor.forClass(FestivalInfo.class);
        verify(festivalInfoMapper).insert(captor.capture());
        // 행사 상세정보는 한국어 원문 그대로다 (후속 단계에서 번역한다)
        assertThat(captor.getValue().getEventPlace()).isEqualTo("경복궁");
        assertThat(captor.getValue().getAddress()).isEqualTo("서울특별시 종로구 사직로 161");
        assertThat(captor.getValue().getSourceType()).isEqualTo("ADMIN");
    }

    @Test
    void updateStoresForeignTranslationsWithTheBase() {
        givenFestivalCategory();
        when(travelInfoMapper.findByIdForUpdate(41L)).thenReturn(existingFestival(41L));
        when(travelInfoMapper.updateTravelInfo(any())).thenReturn(1);
        when(travelInfoMapper.insertPeriod(any())).thenReturn(1);
        when(festivalInfoMapper.findByInfoId(41L)).thenReturn(new FestivalInfo());
        when(festivalInfoMapper.update(any())).thenReturn(1);
        when(travelInfoMapper.findImagesByInfoId(41L)).thenReturn(List.of());
        when(travelInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                storedTranslation(1L, 41L, "ko", "예전 제목", "<p>예전 본문</p>"),
                storedTranslation(2L, 41L, "en", "Old title", "<p>Old body</p>")));

        FestivalEditForm form = editForm();
        setSlot(form.getTranslations(), "en", "Cherry Blossom Festival", "<p>English body</p>");

        adminService.update(41L, form);

        assertThat(captureUpdates())
                .extracting(TravelInfoTranslation::getLanguageCode,
                        TravelInfoTranslation::getTitle)
                .containsExactly(
                        tuple("ko", BASE_TITLE),
                        tuple("en", "Cherry Blossom Festival"));
    }

    @Test
    void clearingOneLanguageDeletesOnlyThatRow() {
        givenFestivalCategory();
        when(travelInfoMapper.findByIdForUpdate(41L)).thenReturn(existingFestival(41L));
        when(travelInfoMapper.updateTravelInfo(any())).thenReturn(1);
        when(travelInfoMapper.insertPeriod(any())).thenReturn(1);
        when(festivalInfoMapper.findByInfoId(41L)).thenReturn(new FestivalInfo());
        when(festivalInfoMapper.update(any())).thenReturn(1);
        when(travelInfoMapper.findImagesByInfoId(41L)).thenReturn(List.of());
        when(travelInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                storedTranslation(1L, 41L, "ko", BASE_TITLE, BASE_CONTENT),
                storedTranslation(2L, 41L, "en", "Old title", "<p>Old body</p>"),
                storedTranslation(3L, 41L, "ja", "古いタイトル", "<p>古い本文</p>")));

        FestivalEditForm form = editForm();
        setSlot(form.getTranslations(), "en", "  ", "<p><br></p>");
        setSlot(form.getTranslations(), "ja", "桜まつり", "<p>日本語本文</p>");

        adminService.update(41L, form);

        verify(travelInfoMapper, times(1)).deleteTranslation(41L, "en");
        verify(travelInfoMapper, never()).deleteTranslation(41L, "ja");
        verify(travelInfoMapper, never()).deleteTranslation(41L, "ko");
    }

    @Test
    void foreignTitleAndContentAreBothOptional() {
        givenFestivalCategory();
        givenGeneratedId(41L);
        when(travelInfoMapper.insertPeriod(any())).thenReturn(1);
        when(festivalInfoMapper.insert(any())).thenReturn(1);
        when(travelInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of());

        FestivalCreateForm form = createForm();
        setSlot(form.getTranslations(), "en", "Cherry Blossom Festival", "");
        setSlot(form.getTranslations(), "ja", "", "<p>日本語本文</p>");

        registrationService.create(form, 7L);

        assertThat(captureInserts())
                .filteredOn(translation -> !"ko".equals(translation.getLanguageCode()))
                .extracting(TravelInfoTranslation::getLanguageCode,
                        TravelInfoTranslation::getTitle,
                        TravelInfoTranslation::getContent)
                .containsExactly(
                        tuple("en", "Cherry Blossom Festival", null),
                        tuple("ja", null, "<p>日本語本文</p>"));
    }

    @Test
    void editScreenPreloadsStoredTranslationsAndLeavesMissingLanguagesEmpty() {
        when(travelInfoMapper.findById(41L)).thenReturn(existingFestival(41L));
        when(travelInfoMapper.findPeriodsByInfoId(41L)).thenReturn(List.of(
                period(41L, "2026-04-01", "2026-04-10")));
        when(travelInfoMapper.findImagesByInfoId(41L)).thenReturn(List.of());
        when(festivalInfoMapper.findByInfoId(41L)).thenReturn(festivalInfo());
        when(travelInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                storedTranslation(1L, 41L, "ko", BASE_TITLE, BASE_CONTENT),
                storedTranslation(2L, 41L, "en", "Cherry Blossom Festival",
                        "<p><img src=\"/uploads/editor/en.png\"></p>")));

        FestivalEditData editData = adminService.getEditData(41L);
        FestivalEditForm form = editData.form();

        assertThat(form.getTranslations())
                .extracting(TravelInfoTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        assertThat(slot(form.getTranslations(), "en").getTitle())
                .isEqualTo("Cherry Blossom Festival");
        // Quill 이미지 HTML 도 그대로 실려 온다
        assertThat(slot(form.getTranslations(), "en").getContent())
                .isEqualTo("<p><img src=\"/uploads/editor/en.png\"></p>");
        // 저장된 줄이 없는 언어는 빈 슬롯으로 남는다
        assertThat(slot(form.getTranslations(), "ja").getTitle()).isEmpty();
        assertThat(slot(form.getTranslations(), "zh-CN").getContent()).isEmpty();
        // 기존 축제 로딩 동작은 그대로다
        assertThat(form.getTitle()).isEqualTo(BASE_TITLE);
        assertThat(form.getContent()).isEqualTo(BASE_CONTENT);
        assertThat(form.getStartDate()).isEqualTo(LocalDate.parse("2026-04-01"));
        assertThat(form.getEndDate()).isEqualTo(LocalDate.parse("2026-04-10"));
        assertThat(form.getEventPlace()).isEqualTo("경복궁");
        assertThat(form.getAddress()).isEqualTo("서울특별시 종로구 사직로 161");
        verify(travelInfoMapper, times(1)).findTranslationsByInfoId(41L);
    }

    @Test
    void festivalInfoFieldsAreNeverTranslatedInThisStep() {
        givenFestivalCategory();
        when(travelInfoMapper.findByIdForUpdate(41L)).thenReturn(existingFestival(41L));
        when(travelInfoMapper.updateTravelInfo(any())).thenReturn(1);
        when(travelInfoMapper.insertPeriod(any())).thenReturn(1);
        when(festivalInfoMapper.findByInfoId(41L)).thenReturn(festivalInfo());
        when(festivalInfoMapper.update(any())).thenReturn(1);
        when(travelInfoMapper.findImagesByInfoId(41L)).thenReturn(List.of());
        when(travelInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of());

        FestivalEditForm form = editForm();
        setSlot(form.getTranslations(), "en", "Cherry Blossom Festival", "<p>English body</p>");

        adminService.update(41L, form);

        ArgumentCaptor<FestivalInfo> captor = ArgumentCaptor.forClass(FestivalInfo.class);
        verify(festivalInfoMapper).update(captor.capture());
        assertThat(captor.getValue().getEventPlace()).isEqualTo("경복궁");
        assertThat(captor.getValue().getAddress()).isEqualTo("서울특별시 종로구 사직로 161");
        assertThat(captor.getValue().getSponsor1()).isEqualTo("국가유산청");
        // 기간·썸네일 처리도 그대로 돈다
        verify(travelInfoMapper).deletePeriodsByInfoId(41L);
        verify(travelInfoMapper).insertPeriod(any());
        verify(travelInfoMapper).clearThumbnailsByInfoId(41L);
    }

    // ─── 행사 상세정보(festival_info) 번역 ───

    @Test
    void registrationStoresEventDetailTranslationsWithTheBase() {
        givenFestivalCategory();
        givenGeneratedId(41L);
        when(travelInfoMapper.insertPeriod(any())).thenReturn(1);
        when(festivalInfoMapper.insert(any())).thenReturn(1);
        when(travelInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of());
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of());

        FestivalCreateForm form = createForm();
        setEventDetailSlot(form.getFestivalInfoTranslations(), "en",
                "Gyeongbokgung Palace", "KRW 60,000 per person");

        registrationService.create(form, 7L);

        assertThat(captureFestivalInfoInserts())
                .extracting(FestivalInfoTranslation::getLanguageCode,
                        FestivalInfoTranslation::getEventPlace)
                .containsExactly(
                        // ko 는 화면의 행사 상세정보 입력에서 나온다
                        tuple("ko", "경복궁"),
                        tuple("en", "Gyeongbokgung Palace"));
    }

    @Test
    void updateStoresEventDetailTranslationsWithTheBase() {
        givenFestivalCategory();
        when(travelInfoMapper.findByIdForUpdate(41L)).thenReturn(existingFestival(41L));
        when(travelInfoMapper.updateTravelInfo(any())).thenReturn(1);
        when(travelInfoMapper.insertPeriod(any())).thenReturn(1);
        when(festivalInfoMapper.findByInfoId(41L)).thenReturn(festivalInfo());
        when(festivalInfoMapper.update(any())).thenReturn(1);
        when(travelInfoMapper.findImagesByInfoId(41L)).thenReturn(List.of());
        when(travelInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of());
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                storedEventDetail(1L, 41L, "ko", "예전 장소"),
                storedEventDetail(2L, 41L, "en", "Old place"),
                storedEventDetail(3L, 41L, "ja", "景福宮")));

        FestivalEditForm form = editForm();
        setEventDetailSlot(form.getFestivalInfoTranslations(), "en",
                "Gyeongbokgung Palace", "KRW 60,000 per person");
        // 일본어는 비워서 저장한다 → 그 언어 줄만 사라져야 한다.

        adminService.update(41L, form);

        verify(festivalInfoMapper, times(1)).deleteTranslation(41L, "ja");
        verify(festivalInfoMapper, never()).deleteTranslation(41L, "ko");
        verify(festivalInfoMapper, never()).deleteTranslation(41L, "en");
        assertThat(captureFestivalInfoUpdates())
                .extracting(FestivalInfoTranslation::getLanguageCode,
                        FestivalInfoTranslation::getEventPlace)
                .containsExactly(tuple("ko", "경복궁"), tuple("en", "Gyeongbokgung Palace"));
    }

    @Test
    void editScreenPreloadsBothTranslationKinds() {
        when(travelInfoMapper.findById(41L)).thenReturn(existingFestival(41L));
        when(travelInfoMapper.findPeriodsByInfoId(41L)).thenReturn(List.of(
                period(41L, "2026-04-01", "2026-04-10")));
        when(travelInfoMapper.findImagesByInfoId(41L)).thenReturn(List.of());
        when(festivalInfoMapper.findByInfoId(41L)).thenReturn(festivalInfo());
        when(travelInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                storedTranslation(2L, 41L, "en", "Cherry Blossom Festival", "<p>English</p>")));
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of(
                storedEventDetail(2L, 41L, "en", "Gyeongbokgung Palace")));

        FestivalEditForm form = adminService.getEditData(41L).form();

        // 같은 언어 슬롯에서 제목·본문과 행사 상세정보가 함께 복원된다.
        assertThat(form.getTranslations())
                .extracting(TravelInfoTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        assertThat(form.getFestivalInfoTranslations())
                .extracting(FestivalInfoTranslationForm::getLanguageCode)
                .containsExactly("ko", "en", "ja", "zh-CN", "zh-TW");
        assertThat(slot(form.getTranslations(), "en").getTitle())
                .isEqualTo("Cherry Blossom Festival");
        assertThat(eventDetailSlot(form.getFestivalInfoTranslations(), "en").getEventPlace())
                .isEqualTo("Gyeongbokgung Palace");
        // 저장된 줄이 없는 언어는 빈 슬롯으로 남는다
        assertThat(eventDetailSlot(form.getFestivalInfoTranslations(), "ja").getEventPlace())
                .isEmpty();
        // 기존 한국어 행사 상세정보 로딩은 그대로다
        assertThat(form.getEventPlace()).isEqualTo("경복궁");
        assertThat(form.getAddress()).isEqualTo("서울특별시 종로구 사직로 161");
        assertThat(form.getStartDate()).isEqualTo(LocalDate.parse("2026-04-01"));
        verify(festivalInfoMapper, times(1)).findTranslationsByInfoId(41L);
    }

    @Test
    void eventDetailTranslationsNeverLeakIntoTheTravelInfoTranslationTable() {
        givenFestivalCategory();
        givenGeneratedId(41L);
        when(travelInfoMapper.insertPeriod(any())).thenReturn(1);
        when(festivalInfoMapper.insert(any())).thenReturn(1);
        when(travelInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of());
        when(festivalInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of());

        FestivalCreateForm form = createForm();
        setSlot(form.getTranslations(), "en", "Cherry Blossom Festival", "<p>English body</p>");
        setEventDetailSlot(form.getFestivalInfoTranslations(), "en",
                "Gyeongbokgung Palace", "KRW 60,000 per person");

        registrationService.create(form, 7L);

        assertThat(captureInserts())
                .extracting(TravelInfoTranslation::getLanguageCode,
                        TravelInfoTranslation::getTitle)
                .containsExactly(tuple("ko", BASE_TITLE), tuple("en", "Cherry Blossom Festival"));
        assertThat(captureFestivalInfoInserts())
                .extracting(FestivalInfoTranslation::getLanguageCode,
                        FestivalInfoTranslation::getEventPlace)
                .containsExactly(tuple("ko", "경복궁"), tuple("en", "Gyeongbokgung Palace"));
    }

    @Test
    void unsupportedLanguageSlotsAreNeverStoredFromTheFestivalForm() {
        givenFestivalCategory();
        givenGeneratedId(41L);
        when(travelInfoMapper.insertPeriod(any())).thenReturn(1);
        when(festivalInfoMapper.insert(any())).thenReturn(1);
        when(travelInfoMapper.findTranslationsByInfoId(41L)).thenReturn(List.of());

        FestivalCreateForm form = createForm();
        form.getTranslations().add(new TravelInfoTranslationForm("zh", "简体标题", "<p>简体正文</p>"));
        form.getTranslations().add(new TravelInfoTranslationForm("en-US", "American", "<p>Body</p>"));
        // 폼이 보낸 ko 값은 무시되고 base 가 이긴다
        setSlot(form.getTranslations(), "ko", "폼이 보낸 제목", "<p>폼이 보낸 본문</p>");

        registrationService.create(form, 7L);

        List<TravelInfoTranslation> inserted = captureInserts();
        assertThat(inserted).hasSize(1);
        assertThat(inserted.get(0).getLanguageCode()).isEqualTo("ko");
        assertThat(inserted.get(0).getTitle()).isEqualTo(BASE_TITLE);
        verify(travelInfoMapper, never()).deleteTranslation(anyLong(), anyString());
    }

    private FestivalCreateForm createForm() {
        FestivalCreateForm form = new FestivalCreateForm();
        form.setTitle(BASE_TITLE);
        form.setContent(BASE_CONTENT);
        form.setScope(TravelInfoScope.DOMESTIC);
        form.setCategoryId(3L);
        form.setStartDate(LocalDate.parse("2026-04-01"));
        form.setEndDate(LocalDate.parse("2026-04-10"));
        form.setEventPlace("경복궁");
        form.setAddress("서울특별시 종로구 사직로 161");
        return form;
    }

    private FestivalEditForm editForm() {
        FestivalEditForm form = new FestivalEditForm();
        form.setTitle(BASE_TITLE);
        form.setContent(BASE_CONTENT);
        form.setScope(TravelInfoScope.DOMESTIC);
        form.setCategoryId(3L);
        form.setStartDate(LocalDate.parse("2026-04-01"));
        form.setEndDate(LocalDate.parse("2026-04-10"));
        form.setEventPlace("경복궁");
        form.setAddress("서울특별시 종로구 사직로 161");
        form.setSponsor1("국가유산청");
        return form;
    }

    private void setSlot(List<TravelInfoTranslationForm> slots, String languageCode,
                         String title, String content) {
        TravelInfoTranslationForm target = slot(slots, languageCode);
        target.setTitle(title);
        target.setContent(content);
    }

    private TravelInfoTranslationForm slot(List<TravelInfoTranslationForm> slots,
                                           String languageCode) {
        return slots.stream()
                .filter(slot -> languageCode.equals(slot.getLanguageCode()))
                .findFirst()
                .orElseThrow();
    }

    private void givenFestivalCategory() {
        InfoCategory category = new InfoCategory();
        category.setId(3L);
        category.setName("문화축제");
        category.setContentType(TravelInfoContentType.FESTIVAL);
        when(infoCategoryMapper.findById(3L)).thenReturn(category);
    }

    private void givenGeneratedId(Long id) {
        doAnswer(invocation -> {
            invocation.getArgument(0, TravelInfo.class).setId(id);
            return 1;
        }).when(travelInfoMapper).insertTravelInfo(any());
    }

    private TravelInfo existingFestival(Long id) {
        TravelInfo info = new TravelInfo();
        info.setId(id);
        info.setTitle(BASE_TITLE);
        info.setContent(BASE_CONTENT);
        info.setScope(TravelInfoScope.DOMESTIC);
        info.setContentType(TravelInfoContentType.FESTIVAL);
        info.setCategoryId(3L);
        return info;
    }

    private FestivalInfo festivalInfo() {
        FestivalInfo info = new FestivalInfo();
        info.setInfoId(41L);
        info.setEventPlace("경복궁");
        info.setAddress("서울특별시 종로구 사직로 161");
        info.setSponsor1("국가유산청");
        return info;
    }

    private InfoPeriod period(Long infoId, String startDate, String endDate) {
        InfoPeriod period = new InfoPeriod();
        period.setInfoId(infoId);
        period.setStartDate(LocalDate.parse(startDate));
        period.setEndDate(LocalDate.parse(endDate));
        return period;
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

    private void setEventDetailSlot(List<FestivalInfoTranslationForm> slots, String languageCode,
                                    String eventPlace, String useTime) {
        FestivalInfoTranslationForm target = eventDetailSlot(slots, languageCode);
        target.setEventPlace(eventPlace);
        target.setUseTime(useTime);
    }

    private FestivalInfoTranslationForm eventDetailSlot(List<FestivalInfoTranslationForm> slots,
                                                        String languageCode) {
        return slots.stream()
                .filter(slot -> languageCode.equals(slot.getLanguageCode()))
                .findFirst()
                .orElseThrow();
    }

    private FestivalInfoTranslation storedEventDetail(Long id, Long infoId, String languageCode,
                                                      String eventPlace) {
        FestivalInfoTranslation translation = new FestivalInfoTranslation();
        translation.setId(id);
        translation.setInfoId(infoId);
        translation.setLanguageCode(languageCode);
        translation.setEventPlace(eventPlace);
        return translation;
    }

    private List<FestivalInfoTranslation> captureFestivalInfoInserts() {
        ArgumentCaptor<FestivalInfoTranslation> captor =
                ArgumentCaptor.forClass(FestivalInfoTranslation.class);
        verify(festivalInfoMapper, org.mockito.Mockito.atLeastOnce())
                .insertTranslation(captor.capture());
        return captor.getAllValues();
    }

    private List<FestivalInfoTranslation> captureFestivalInfoUpdates() {
        ArgumentCaptor<FestivalInfoTranslation> captor =
                ArgumentCaptor.forClass(FestivalInfoTranslation.class);
        verify(festivalInfoMapper, org.mockito.Mockito.atLeastOnce())
                .updateTranslation(captor.capture());
        return captor.getAllValues();
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
