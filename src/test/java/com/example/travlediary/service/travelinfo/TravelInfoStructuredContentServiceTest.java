package com.example.travlediary.service.travelinfo;

import com.example.travlediary.config.i18n.SupportedLanguage;
import com.example.travlediary.dto.AdminTravelInfoDetailDto;
import com.example.travlediary.dto.TravelInfoDetailDto;
import com.example.travlediary.dto.TravelInfoForm;
import com.example.travlediary.dto.TravelInfoTranslationForm;
import com.example.travlediary.model.InfoCategory;
import com.example.travlediary.model.TravelInfo;
import com.example.travlediary.model.TravelInfoContentFormat;
import com.example.travlediary.model.TravelInfoContentType;
import com.example.travlediary.model.TravelInfoScope;
import com.example.travlediary.model.TravelInfoTranslation;
import com.example.travlediary.repository.bookmark.BookmarkMapper;
import com.example.travlediary.repository.category.CategoryMapper;
import com.example.travlediary.repository.category.CountryCategoryMapper;
import com.example.travlediary.repository.category.InfoCategoryMapper;
import com.example.travlediary.repository.travelinfo.FestivalInfoMapper;
import com.example.travlediary.repository.travelinfo.TravelInfoMapper;
import com.example.travlediary.seo.SeoTextUtils;
import com.example.travlediary.service.category.LocalizedReferenceNameResolver;
import com.example.travlediary.service.category.ReferenceNameLocalizationService;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.post.PostContentSanitizer;
import com.example.travlediary.service.travelinfo.structured.StructuredBlock;
import com.example.travlediary.service.travelinfo.structured.StructuredContent;
import com.example.travlediary.service.travelinfo.structured.StructuredContentTestSupport;
import com.example.travlediary.service.travelinfo.structured.StructuredContentValidationException;
import com.example.travlediary.service.travelinfo.structured.StructuredImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STRUCTURED 본문이 여행정보 등록·수정·조회·번역 흐름을 타는지 본다. QUILL 경로가 그대로인지도 함께 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class TravelInfoStructuredContentServiceTest {

    private static final String URL =
            "/uploads/travel-info/content/123e4567-e89b-12d3-a456-426614174000.webp";
    private static final String IMAGE =
            "{\"height\":1333,\"width\":2000,\"url\":\"" + URL + "\"}";

    /** 공백·키 순서가 제각각이고, 예전 섹션 제목의 짧은 소개(lead)가 남아 있는 관리자 입력. */
    private static final String SUBMITTED_JSON = """
            {  "blocks" : [
                 {"title": "서울 궁 투어 😀", "lead": "다섯 궁을 하루에", "id": "intro", "type": "SECTION_TITLE"},
                 {"type": "CALLOUT", "text": "<b>월요일</b> 휴궁", "id": "tip"},
                 {"id": "palace", "type": "IMAGE_TEXT", "imagePosition": "LEFT", "image": %s,
                  "alt": "근정전 사진", "text": "조선의 법궁"},
                 {"type": "IMAGE_SLIDER", "id": "gyeongbok", "title": "경복궁 주요 전각",
                  "items": [{"caption": "정문", "title": "광화문", "alt": "광화문 사진", "image": %s, "id": "i1"}]}
               ],
               "version" : 1 }
            """.formatted(IMAGE, IMAGE);

    private static final String CANONICAL_JSON = "{\"version\":1,\"blocks\":["
            + "{\"type\":\"SECTION_TITLE\",\"id\":\"intro\",\"title\":\"서울 궁 투어 😀\"},"
            + "{\"type\":\"CALLOUT\",\"id\":\"tip\",\"text\":\"<b>월요일</b> 휴궁\"},"
            + "{\"type\":\"IMAGE_TEXT\",\"id\":\"palace\",\"imagePosition\":\"LEFT\","
            + "\"image\":{\"url\":\"" + URL + "\",\"width\":2000,\"height\":1333},"
            + "\"alt\":\"근정전 사진\",\"text\":\"조선의 법궁\"},"
            + "{\"type\":\"IMAGE_SLIDER\",\"id\":\"gyeongbok\",\"title\":\"경복궁 주요 전각\",\"items\":["
            + "{\"id\":\"i1\",\"image\":{\"url\":\"" + URL + "\",\"width\":2000,\"height\":1333},"
            + "\"alt\":\"광화문 사진\",\"title\":\"광화문\",\"caption\":\"정문\"}]}]}";

    private static final String DERIVED_CONTENT = "<h2>서울 궁 투어 &#x1F600;</h2>"
            + "<p>&lt;b&gt;월요일&lt;/b&gt; 휴궁</p>"
            + "<p>조선의 법궁</p>"
            + "<h3>경복궁 주요 전각</h3><ul><li>광화문<br>정문</li></ul>";

    @Mock private TravelInfoMapper travelInfoMapper;
    @Mock private FestivalInfoMapper festivalInfoMapper;
    @Mock private BookmarkMapper bookmarkMapper;
    @Mock private InfoCategoryMapper infoCategoryMapper;
    @Mock private FileUploadService fileUploadService;

    private TravelInfoService travelInfoService;

    @BeforeEach
    void setUp() {
        travelInfoService = new TravelInfoService(
                travelInfoMapper, festivalInfoMapper, bookmarkMapper, infoCategoryMapper,
                new PostContentSanitizer(), fileUploadService,
                new TravelInfoLocalizationService(travelInfoMapper),
                new ReferenceNameLocalizationService(
                        mock(CountryCategoryMapper.class), mock(CategoryMapper.class),
                        infoCategoryMapper, new LocalizedReferenceNameResolver()),
                StructuredContentTestSupport.structuredContentService());
    }

    // ---- create -------------------------------------------------------------------------

    @Test
    void quillCreateKeepsTheExistingSanitizedPathWithoutStructuredValues() {
        TravelInfoForm form = form(TravelInfoContentType.GENERAL, TravelInfoContentFormat.QUILL);
        form.setContent("<p class=\"ql-align-center\"><img src=\"/uploads/editor/a.png\" "
                + "class=\"ql-image-size-50\" onerror=\"x\"></p>");
        // QUILL 이면 구조화 입력이 넘어와도 쓰지 않는다.
        form.setStructuredContent(SUBMITTED_JSON);
        allowCategory(TravelInfoContentType.GENERAL);
        stubInsert(100L);

        travelInfoService.create(form, 7L);

        TravelInfo inserted = captureInsert();
        assertThat(inserted.getContentFormat()).isEqualTo(TravelInfoContentFormat.QUILL);
        assertThat(inserted.getStructuredContent()).isNull();
        assertThat(inserted.getContent()).isEqualTo(
                "<p class=\"ql-align-center\"><img src=\"/uploads/editor/a.png\" class=\"ql-image-size-50\"></p>");
        TravelInfoTranslation korean = captureTranslationInserts().get(0);
        assertThat(korean.getContent()).isEqualTo(inserted.getContent());
        assertThat(korean.getStructuredText()).isNull();
    }

    @Test
    void structuredGeneralCreateStoresCanonicalJsonAndDerivedContent() {
        TravelInfoForm form = form(TravelInfoContentType.GENERAL, TravelInfoContentFormat.STRUCTURED);
        form.setStructuredContent(SUBMITTED_JSON);
        allowCategory(TravelInfoContentType.GENERAL);
        stubInsert(100L);

        Long id = travelInfoService.create(form, 7L);

        assertThat(id).isEqualTo(100L);
        TravelInfo inserted = captureInsert();
        assertThat(inserted.getContentFormat()).isEqualTo(TravelInfoContentFormat.STRUCTURED);
        // 보낸 문자열이 아니라 서버가 읽은 model 을 다시 쓴 JSON. (공백·키 순서·예전 lead 가 사라진다)
        assertThat(inserted.getStructuredContent()).isEqualTo(CANONICAL_JSON);
        // 파생 content 는 sanitizer 를 다시 거치지 않는다. 거쳤다면 &#x1F600; 가 4바이트 문자로 풀렸다.
        assertThat(inserted.getContent()).isEqualTo(DERIVED_CONTENT);
        assertThat(inserted.getContent().codePoints().noneMatch(Character::isSupplementaryCodePoint))
                .isTrue();
        assertThat(inserted.getContent()).doesNotContain("<img", "<b>");

        TravelInfoTranslation korean = captureTranslationInserts().get(0);
        assertThat(korean.getLanguageCode()).isEqualTo("ko");
        assertThat(korean.getContent()).isEqualTo(DERIVED_CONTENT);
        assertThat(korean.getStructuredText()).isNull();
    }

    @Test
    void structuredGuideCreateIsAllowedWithoutScope() {
        TravelInfoForm form = form(TravelInfoContentType.GUIDE, TravelInfoContentFormat.STRUCTURED);
        form.setStructuredContent(SUBMITTED_JSON);
        allowCategory(TravelInfoContentType.GUIDE);
        stubInsert(101L);

        travelInfoService.create(form, 7L);

        TravelInfo inserted = captureInsert();
        assertThat(inserted.getContentType()).isEqualTo(TravelInfoContentType.GUIDE);
        assertThat(inserted.getScope()).isNull();
        assertThat(inserted.getContentFormat()).isEqualTo(TravelInfoContentFormat.STRUCTURED);
        assertThat(inserted.getStructuredContent()).isEqualTo(CANONICAL_JSON);
    }

    @Test
    void imagesWithoutAltAndAnImageOnlyArticleAreSaved() {
        String image = "{\"url\":\"" + URL + "\",\"width\":1200,\"height\":800}";
        TravelInfoForm form = form(TravelInfoContentType.GENERAL, TravelInfoContentFormat.STRUCTURED);
        form.setContent(""); // 숨은 Quill 본문은 비어 있다. STRUCTURED 저장에 쓰지 않는다.
        // 세 이미지 블록 모두 alt 가 없다. (IMAGE_TEXT 는 본문만 필수라 본문만 넣는다)
        form.setStructuredContent("{\"version\":1,\"blocks\":["
                + "{\"type\":\"FULL_IMAGE\",\"id\":\"full\",\"image\":" + image + ",\"alt\":\"\"},"
                + "{\"type\":\"IMAGE_TEXT\",\"id\":\"split\",\"imagePosition\":\"LEFT\",\"image\":" + image
                + ",\"text\":\"본문\"},"
                + "{\"type\":\"IMAGE_SLIDER\",\"id\":\"slider\",\"items\":[{\"id\":\"i1\",\"image\":" + image + "}]}"
                + "]}");
        allowCategory(TravelInfoContentType.GENERAL);
        stubInsert(100L);

        travelInfoService.create(form, 7L);

        TravelInfo inserted = captureInsert();
        assertThat(inserted.getStructuredContent())
                .doesNotContain("\"alt\"")
                .contains("{\"type\":\"FULL_IMAGE\",\"id\":\"full\",\"image\":{\"url\":\"" + URL);
        assertThat(inserted.getContent()).isEqualTo("<p>본문</p>");

        TravelInfoForm imageOnly = form(TravelInfoContentType.GENERAL, TravelInfoContentFormat.STRUCTURED);
        imageOnly.setStructuredContent("{\"version\":1,\"blocks\":[{\"type\":\"FULL_IMAGE\",\"id\":\"full\",\"image\":"
                + image + "}]}");
        doAnswer(invocation -> {
            TravelInfo travelInfo = invocation.getArgument(0);
            travelInfo.setId(101L);
            return 1;
        }).when(travelInfoMapper).insertTravelInfo(any());

        // 글자가 하나도 없는 사진만의 글도 저장된다. 파생 content(검색/SEO 용)는 비어 있다.
        assertThat(travelInfoService.create(imageOnly, 7L)).isEqualTo(101L);
    }

    @Test
    void festivalCannotBeStructuredAndIsNotSilentlyTurnedIntoQuill() {
        TravelInfoForm form = form(TravelInfoContentType.FESTIVAL, TravelInfoContentFormat.STRUCTURED);
        form.setStructuredContent(SUBMITTED_JSON);

        assertValidation("contentFormat", "축제·행사는 구조화 에디터로 작성할 수 없습니다.",
                () -> travelInfoService.create(form, 7L));
        verify(travelInfoMapper, never()).insertTravelInfo(any());
    }

    @Test
    void malformedOrMissingStructuredJsonIsRejectedBeforeInsert() {
        TravelInfoForm malformed = form(TravelInfoContentType.GENERAL, TravelInfoContentFormat.STRUCTURED);
        malformed.setStructuredContent("{\"version\":1,\"blocks\":[");
        assertThatThrownBy(() -> travelInfoService.create(malformed, 7L))
                .isInstanceOf(StructuredContentValidationException.class)
                .hasMessageContaining("구조화 콘텐츠 형식이 올바르지 않습니다.")
                .extracting(exception -> ((TravelInfoValidationException) exception).getField())
                .isEqualTo("structuredContent");

        TravelInfoForm unknownField = form(TravelInfoContentType.GENERAL, TravelInfoContentFormat.STRUCTURED);
        unknownField.setStructuredContent("{\"version\":1,\"blocks\":[{\"id\":\"a\",\"type\":\"CALLOUT\","
                + "\"text\":\"문구\",\"style\":\"color:red\"}]}");
        assertThatThrownBy(() -> travelInfoService.create(unknownField, 7L))
                .hasMessageContaining("허용하지 않는 항목이 있습니다: style");

        TravelInfoForm missing = form(TravelInfoContentType.GENERAL, TravelInfoContentFormat.STRUCTURED);
        missing.setContent("<p>Quill 본문은 STRUCTURED 에서 쓰지 않는다</p>");
        assertValidation("structuredContent", "구조화 콘텐츠가 비어 있습니다.",
                () -> travelInfoService.create(missing, 7L));

        TravelInfoForm noFormat = form(TravelInfoContentType.GENERAL, null);
        assertValidation("contentFormat", "작성 방식을 선택해 주세요.",
                () -> travelInfoService.create(noFormat, 7L));

        verify(travelInfoMapper, never()).insertTravelInfo(any());
    }

    @Test
    void derivedContentStaysUsableForSearchAndSeoSummary() {
        TravelInfoForm form = form(TravelInfoContentType.GENERAL, TravelInfoContentFormat.STRUCTURED);
        form.setStructuredContent(SUBMITTED_JSON);
        allowCategory(TravelInfoContentType.GENERAL);
        stubInsert(100L);

        travelInfoService.create(form, 7L);

        String content = captureInsert().getContent();
        // 통합검색은 content LIKE 를 쓴다. 블록 글이 escape 없이 평문 그대로 들어 있어야 잡힌다.
        assertThat(content).contains("서울 궁 투어", "월요일", "조선의 법궁", "경복궁 주요 전각", "광화문", "정문");
        assertThat(TravelInfoContent.hasContent(content)).isTrue();
        // SEO 설명은 파생 HTML 의 글만 모으며, 숫자 참조는 원래 문자로 돌아온다.
        assertThat(SeoTextUtils.summary(content))
                .isEqualTo("서울 궁 투어 😀 <b>월요일</b> 휴궁 조선의 법궁 경복궁 주요 전각 광화문 정문");
    }

    // ---- update -------------------------------------------------------------------------

    @Test
    void quillUpdateKeepsTheExistingPathAndNeverTouchesStructuredJson() {
        TravelInfo existing = existing(TravelInfoContentFormat.QUILL);
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(existing);
        when(travelInfoMapper.updateTravelInfo(existing)).thenReturn(1);
        allowCategory(TravelInfoContentType.GENERAL);

        travelInfoService.update(10L, form(TravelInfoContentType.GENERAL, TravelInfoContentFormat.QUILL));

        assertThat(existing.getContent()).isEqualTo("<p>봄 여행 정보</p>");
        verify(travelInfoMapper, never()).updateStructuredContent(any(), any());
    }

    @Test
    void structuredUpdateChangesDerivedContentAndCanonicalJsonTogether() {
        TravelInfo existing = existing(TravelInfoContentFormat.STRUCTURED);
        existing.setStructuredContent("{\"version\": 1, \"blocks\": []}");
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(existing);
        when(travelInfoMapper.updateTravelInfo(existing)).thenReturn(1);
        when(travelInfoMapper.updateStructuredContent(10L, CANONICAL_JSON)).thenReturn(1);
        allowCategory(TravelInfoContentType.GENERAL);

        TravelInfoForm form = form(TravelInfoContentType.GENERAL, TravelInfoContentFormat.STRUCTURED);
        form.setStructuredContent(SUBMITTED_JSON);
        travelInfoService.update(10L, form);

        // 공용 updateTravelInfo 는 파생 content 를, 전용 update 는 원문 JSON 을 같은 트랜잭션에서 바꾼다.
        assertThat(existing.getContent()).isEqualTo(DERIVED_CONTENT);
        verify(travelInfoMapper).updateTravelInfo(existing);
        verify(travelInfoMapper).updateStructuredContent(10L, CANONICAL_JSON);
        assertThat(captureTranslationInserts().get(0).getContent()).isEqualTo(DERIVED_CONTENT);
    }

    @Test
    void structuredUpdateFailsWhenTheGuardedJsonUpdateDoesNotApply() {
        TravelInfo existing = existing(TravelInfoContentFormat.STRUCTURED);
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(existing);
        when(travelInfoMapper.updateTravelInfo(existing)).thenReturn(1);
        when(travelInfoMapper.updateStructuredContent(eq(10L), anyString())).thenReturn(0);
        allowCategory(TravelInfoContentType.GENERAL);

        TravelInfoForm form = form(TravelInfoContentType.GENERAL, TravelInfoContentFormat.STRUCTURED);
        form.setStructuredContent(SUBMITTED_JSON);

        assertThatThrownBy(() -> travelInfoService.update(10L, form))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("구조화 콘텐츠 저장에 실패했습니다.");
    }

    @Test
    void contentFormatCannotChangeAfterCreation() {
        when(travelInfoMapper.findByIdForUpdate(10L))
                .thenReturn(existing(TravelInfoContentFormat.QUILL),
                        existing(TravelInfoContentFormat.STRUCTURED),
                        existing(TravelInfoContentFormat.STRUCTURED));

        TravelInfoForm toStructured = form(TravelInfoContentType.GENERAL, TravelInfoContentFormat.STRUCTURED);
        toStructured.setStructuredContent(SUBMITTED_JSON);
        assertValidation("contentFormat", "작성 방식은 등록한 뒤 바꿀 수 없습니다.",
                () -> travelInfoService.update(10L, toStructured));

        // 지금 관리자 화면처럼 작성 방식을 보내지 않으면 QUILL 로 들어온다. STRUCTURED 글을 덮어쓰지 않는다.
        assertValidation("contentFormat", "작성 방식은 등록한 뒤 바꿀 수 없습니다.",
                () -> travelInfoService.update(10L,
                        form(TravelInfoContentType.GENERAL, TravelInfoContentFormat.QUILL)));
        assertValidation("contentFormat", "작성 방식은 등록한 뒤 바꿀 수 없습니다.",
                () -> travelInfoService.update(10L, form(TravelInfoContentType.GENERAL, null)));

        verify(travelInfoMapper, never()).updateTravelInfo(any());
        verify(travelInfoMapper, never()).updateStructuredContent(any(), any());
        verify(infoCategoryMapper, never()).findById(any());
    }

    @Test
    void structuredArticleCannotBeMovedToFestival() {
        when(travelInfoMapper.findByIdForUpdate(10L)).thenReturn(existing(TravelInfoContentFormat.STRUCTURED));
        TravelInfoForm form = form(TravelInfoContentType.FESTIVAL, TravelInfoContentFormat.STRUCTURED);
        form.setStructuredContent(SUBMITTED_JSON);

        assertValidation("contentFormat", "축제·행사는 구조화 에디터로 작성할 수 없습니다.",
                () -> travelInfoService.update(10L, form));
        verify(travelInfoMapper, never()).updateTravelInfo(any());
    }

    // ---- read ---------------------------------------------------------------------------

    @Test
    void editFormRestoresStructuredFormatWithCanonicalJsonForBaseAndTranslations() {
        TravelInfo stored = existing(TravelInfoContentFormat.STRUCTURED);
        // MySQL JSON 은 키 순서와 공백을 자기 방식으로 다시 적어 돌려준다.
        stored.setStructuredContent(SUBMITTED_JSON);
        when(travelInfoMapper.findById(10L)).thenReturn(stored);
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                translation("en", "Seoul palaces", "<h2>Seoul</h2>",
                        "{\"blocks\": {\"intro\": {\"title\": \"Seoul Palace Tour\"}}}")));

        TravelInfoForm form = travelInfoService.getForm(10L);

        assertThat(form.getContentFormat()).isEqualTo(TravelInfoContentFormat.STRUCTURED);
        assertThat(form.getStructuredContent()).isEqualTo(CANONICAL_JSON);
        TravelInfoTranslationForm english = slot(form, "en");
        assertThat(english.getTitle()).isEqualTo("Seoul palaces");
        assertThat(english.getStructuredText())
                .isEqualTo("{\"blocks\":{\"intro\":{\"title\":\"Seoul Palace Tour\"}}}");
        assertThat(slot(form, "ja").getStructuredText()).isNull();
    }

    @Test
    void quillEditFormLeavesStructuredValuesEmpty() {
        when(travelInfoMapper.findById(10L)).thenReturn(existing(TravelInfoContentFormat.QUILL));

        TravelInfoForm form = travelInfoService.getForm(10L);

        assertThat(form.getContentFormat()).isEqualTo(TravelInfoContentFormat.QUILL);
        assertThat(form.getContent()).isEqualTo("<p>기존 본문</p>");
        assertThat(form.getStructuredContent()).isNull();
    }

    @Test
    void publicDetailCarriesParsedBlocksInsteadOfRawJson() {
        when(travelInfoMapper.incrementPublicViews(10L)).thenReturn(1);
        when(travelInfoMapper.findPublicDetailById(10L)).thenReturn(publicDetail(SUBMITTED_JSON));

        TravelInfoDetailDto detail = travelInfoService.getPublicDetail(10L);

        assertThat(detail.getContentFormat()).isEqualTo(TravelInfoContentFormat.STRUCTURED);
        assertThat(detail.getStructuredContent().blocks()).extracting(StructuredBlock::id)
                .containsExactly("intro", "tip", "palace", "gyeongbok");
        assertThat(detail.getContent()).contains("서울 궁 투어");
    }

    @Test
    void publicDetailWithUnreadableStoredJsonKeepsDerivedContentInsteadOfFailing() {
        when(travelInfoMapper.incrementPublicViews(10L)).thenReturn(1);
        when(travelInfoMapper.findPublicDetailById(10L))
                .thenReturn(publicDetail("{\"version\":1,\"blocks\":[{\"id\":\"a\",\"type\":\"VIDEO\"}]}"));

        TravelInfoDetailDto detail = travelInfoService.getPublicDetail(10L);

        assertThat(detail.getStructuredContent()).isNull();
        assertThat(detail.getContent()).isEqualTo("<h2>서울 궁 투어</h2>");
    }

    @Test
    void publicDetailLocalizesBlockTextButKeepsBaseImagesAndOrder() {
        TravelInfoDetailDto detail = publicDetail(CANONICAL_JSON);
        detail.setStructuredContent(StructuredContentTestSupport.structuredContentService()
                .readStored(CANONICAL_JSON));
        when(travelInfoMapper.findTranslationsByInfoId(10L)).thenReturn(List.of(
                translation("ko", "서울 궁 투어", DERIVED_CONTENT, null),
                translation("en", "Seoul palaces", "<h2>Seoul Palace Tour</h2>",
                        "{\"blocks\":{\"intro\":{\"title\":\"Seoul Palace Tour\"},"
                                + "\"gyeongbok\":{\"items\":{\"i1\":{\"title\":\"Gwanghwamun\"}}}}}")));

        travelInfoService.localizePublicDetail(detail, SupportedLanguage.ENGLISH);

        assertThat(detail.getTitle()).isEqualTo("Seoul palaces");
        assertThat(detail.getContent()).isEqualTo("<h2>Seoul Palace Tour</h2>");
        StructuredContent localized = detail.getStructuredContent();
        assertThat(((StructuredBlock.SectionTitle) localized.blocks().get(0)).title())
                .isEqualTo("Seoul Palace Tour");
        StructuredBlock.SliderItem item =
                ((StructuredBlock.ImageSlider) localized.blocks().get(3)).items().get(0);
        assertThat(item.title()).isEqualTo("Gwanghwamun");
        // 번역에 없는 칸은 한국어 원문, 이미지는 언제나 원문 것.
        assertThat(item.caption()).isEqualTo("정문");
        assertThat(item.image()).isEqualTo(new StructuredImage(URL, 2000, 1333));
        assertThat(((StructuredBlock.Callout) localized.blocks().get(1)).text()).isEqualTo("<b>월요일</b> 휴궁");
        // 번역은 한 번만 읽는다.
        verify(travelInfoMapper, times(1)).findTranslationsByInfoId(10L);
    }

    @Test
    void adminDetailCarriesFormatAndParsedBlocks() {
        TravelInfo stored = existing(TravelInfoContentFormat.STRUCTURED);
        stored.setStructuredContent(SUBMITTED_JSON);
        when(travelInfoMapper.findById(10L)).thenReturn(stored);

        AdminTravelInfoDetailDto detail = travelInfoService.getAdminDetail(10L);

        assertThat(detail.getContentFormat()).isEqualTo(TravelInfoContentFormat.STRUCTURED);
        assertThat(detail.getStructuredContent().blocks()).hasSize(4);
    }

    // ---- translation --------------------------------------------------------------------

    @Test
    void structuredTranslationKeepsOnlyKnownBlocksItemsAndFieldsWithLocalizedDerivedContent() {
        TravelInfoForm form = form(TravelInfoContentType.GENERAL, TravelInfoContentFormat.STRUCTURED);
        form.setStructuredContent(SUBMITTED_JSON);
        TravelInfoTranslationForm english = slot(form, "en");
        english.setTitle("Seoul palaces");
        // Quill 칸은 STRUCTURED 에서 쓰지 않는다.
        english.setContent("<p>ignored quill html</p>");
        english.setStructuredText("""
                {"blocks": {
                  "intro": {"title": "Seoul Palace Tour 🏯"},
                  "tip": {"text": "Closed on Mondays", "caption": "not a callout field"},
                  "gyeongbok": {"title": "Main halls", "items": {
                    "i1": {"title": "Gwanghwamun"},
                    "deleted-item": {"title": "Ghost item"}
                  }},
                  "deleted-block": {"title": "Ghost block"}
                }}""");
        allowCategory(TravelInfoContentType.GENERAL);
        stubInsert(100L);

        travelInfoService.create(form, 7L);

        List<TravelInfoTranslation> rows = captureTranslationInserts();
        assertThat(rows).extracting(TravelInfoTranslation::getLanguageCode).containsExactly("ko", "en");
        TravelInfoTranslation en = rows.get(1);
        assertThat(en.getTitle()).isEqualTo("Seoul palaces");
        // 원문에 없는 블록·이미지와 그 블록 종류에 없는 칸은 빠지고, 키 순서로 정규화된다.
        assertThat(en.getStructuredText()).isEqualTo("{\"blocks\":{"
                + "\"gyeongbok\":{\"title\":\"Main halls\",\"items\":{\"i1\":{\"title\":\"Gwanghwamun\"}}},"
                + "\"intro\":{\"title\":\"Seoul Palace Tour 🏯\"},"
                + "\"tip\":{\"text\":\"Closed on Mondays\"}}}");
        // 그 언어 기준 파생 HTML. 번역이 없는 칸(IMAGE_TEXT 본문, 이미지 캡션)은 한국어 원문을 쓴다.
        assertThat(en.getContent()).isEqualTo("<h2>Seoul Palace Tour &#x1F3EF;</h2>"
                + "<p>Closed on Mondays</p>"
                + "<p>조선의 법궁</p>"
                + "<h3>Main halls</h3><ul><li>Gwanghwamun<br>정문</li></ul>");
    }

    @Test
    void emptyStructuredTranslationsAreNotStoredAndQuillContentSlotIsIgnored() {
        TravelInfoForm form = form(TravelInfoContentType.GENERAL, TravelInfoContentFormat.STRUCTURED);
        form.setStructuredContent(SUBMITTED_JSON);
        slot(form, "ja").setContent("<p>日本語の本文</p>");
        slot(form, "en").setStructuredText("{\"blocks\":{\"deleted-block\":{\"title\":\"Ghost\"}}}");
        allowCategory(TravelInfoContentType.GENERAL);
        stubInsert(100L);

        travelInfoService.create(form, 7L);

        assertThat(captureTranslationInserts()).extracting(TravelInfoTranslation::getLanguageCode)
                .containsExactly("ko");
    }

    @Test
    void structuredTranslationUsesTheLimitOfEachBlockType() {
        assertTranslationRejected("{\"blocks\":{\"tip\":{\"text\":\"" + "A".repeat(501) + "\"}}}",
                "English 번역 · 2번째 블록(강조 문구): 강조 문구는 500자 이하로 입력해 주세요.");
        assertTranslationRejected("{\"blocks\":{\"palace\":{\"text\":\"" + "A".repeat(3001) + "\"}}}",
                "English 번역 · 3번째 블록(이미지 + 글): 본문은 3000자 이하로 입력해 주세요.");
        // 모든 블록에 같은 한도인 칸은 번역 모양 검사에서 먼저 걸린다.
        assertTranslationRejected("{\"blocks\":{\"intro\":{\"title\":\"" + "A".repeat(121) + "\"}}}",
                "English 번역 · 번역 블록(intro): 제목은 120자 이하로 입력해 주세요.");
        assertTranslationRejected("{\"blocks\":{\"gyeongbok\":{\"items\":{\"i1\":{\"caption\":\""
                        + "A".repeat(301) + "\"}}}}}",
                "English 번역 · 번역 블록(gyeongbok) 이미지(i1): 캡션은 300자 이하로 입력해 주세요.");
        assertTranslationRejected("{\"blocks\":{\"gyeongbok\":{\"items\":{\"i1\":{\"title\":\""
                        + "A".repeat(101) + "\"}}}}}",
                "English 번역 · 번역 블록(gyeongbok) 이미지(i1): 이미지 제목은 100자 이하로 입력해 주세요.");
    }

    @Test
    void structuredTranslationCannotChangeImagesTypeOrLayout() {
        assertTranslationRejected("{\"blocks\":{\"palace\":{\"image\":{\"url\":\"" + URL + "\"}}}}",
                "English 번역 · 허용하지 않는 항목이 있습니다: image");
        assertTranslationRejected("{\"blocks\":{\"palace\":{\"imagePosition\":\"RIGHT\"}}}",
                "English 번역 · 허용하지 않는 항목이 있습니다: imagePosition");
        assertTranslationRejected("{\"blocks\":{\"tip\":{\"type\":\"SECTION_TITLE\"}}}",
                "English 번역 · 허용하지 않는 항목이 있습니다: type");
    }

    @Test
    void quillTranslationsNeverStoreStructuredText() {
        TravelInfoForm form = form(TravelInfoContentType.GENERAL, TravelInfoContentFormat.QUILL);
        TravelInfoTranslationForm english = slot(form, "en");
        english.setTitle("Spring trip");
        english.setContent("<p>Spring</p>");
        english.setStructuredText("{\"blocks\":{\"intro\":{\"title\":\"ignored\"}}}");
        allowCategory(TravelInfoContentType.GENERAL);
        stubInsert(100L);

        travelInfoService.create(form, 7L);

        TravelInfoTranslation en = captureTranslationInserts().get(1);
        assertThat(en.getContent()).isEqualTo("<p>Spring</p>");
        assertThat(en.getStructuredText()).isNull();
    }

    // ---- helpers ------------------------------------------------------------------------

    private void assertTranslationRejected(String structuredText, String message) {
        TravelInfoForm form = form(TravelInfoContentType.GENERAL, TravelInfoContentFormat.STRUCTURED);
        form.setStructuredContent(SUBMITTED_JSON);
        slot(form, "en").setStructuredText(structuredText);
        when(infoCategoryMapper.findById(3L)).thenReturn(category(TravelInfoContentType.GENERAL));
        stubInsert(100L);

        assertThatThrownBy(() -> travelInfoService.create(form, 7L))
                .isInstanceOf(StructuredContentValidationException.class)
                .hasMessageStartingWith(message)
                .extracting(exception -> ((TravelInfoValidationException) exception).getField())
                .isEqualTo("translations");
    }

    private void assertValidation(String field, String message, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(TravelInfoValidationException.class)
                .hasMessage(message)
                .extracting(exception -> ((TravelInfoValidationException) exception).getField())
                .isEqualTo(field);
    }

    private TravelInfoForm form(TravelInfoContentType contentType, TravelInfoContentFormat contentFormat) {
        TravelInfoForm form = new TravelInfoForm();
        form.setTitle("서울 궁 투어");
        form.setContent("<p>봄 여행 정보</p>");
        form.setContentFormat(contentFormat);
        form.setScope(TravelInfoScope.DOMESTIC);
        form.setContentType(contentType);
        form.setCategoryId(3L);
        return form;
    }

    private TravelInfo existing(TravelInfoContentFormat contentFormat) {
        TravelInfo info = new TravelInfo();
        info.setId(10L);
        info.setTitle("기존 제목");
        info.setContent("<p>기존 본문</p>");
        info.setContentFormat(contentFormat);
        info.setScope(TravelInfoScope.DOMESTIC);
        info.setContentType(TravelInfoContentType.GENERAL);
        info.setCategoryId(3L);
        info.setViews(0);
        info.setUserId(7L);
        return info;
    }

    private TravelInfoDetailDto publicDetail(String structuredJson) {
        TravelInfoDetailDto detail = new TravelInfoDetailDto();
        detail.setId(10L);
        detail.setTitle("서울 궁 투어");
        detail.setContentType(TravelInfoContentType.GENERAL);
        detail.setContent("<h2>서울 궁 투어</h2>");
        detail.setContentFormat(TravelInfoContentFormat.STRUCTURED);
        detail.setStructuredContentJson(structuredJson);
        return detail;
    }

    private TravelInfoTranslation translation(String languageCode, String title, String content,
                                              String structuredText) {
        TravelInfoTranslation translation = new TravelInfoTranslation();
        translation.setTravelInfoId(10L);
        translation.setLanguageCode(languageCode);
        translation.setTitle(title);
        translation.setContent(content);
        translation.setStructuredText(structuredText);
        return translation;
    }

    private TravelInfoTranslationForm slot(TravelInfoForm form, String languageCode) {
        return form.getTranslations().stream()
                .filter(slot -> languageCode.equals(slot.getLanguageCode()))
                .findFirst()
                .orElseThrow();
    }

    private void allowCategory(TravelInfoContentType contentType) {
        when(infoCategoryMapper.findById(3L)).thenReturn(category(contentType));
    }

    private InfoCategory category(TravelInfoContentType contentType) {
        InfoCategory category = new InfoCategory();
        category.setId(3L);
        category.setName("계절여행");
        category.setContentType(contentType);
        category.setIsVisible(true);
        return category;
    }

    /** doAnswer 형태라 한 테스트에서 여러 번 불러도 앞의 answer 가 null 인자로 실행되지 않는다. */
    private void stubInsert(Long id) {
        doAnswer(invocation -> {
            TravelInfo travelInfo = invocation.getArgument(0);
            travelInfo.setId(id);
            return 1;
        }).when(travelInfoMapper).insertTravelInfo(any());
    }

    private TravelInfo captureInsert() {
        ArgumentCaptor<TravelInfo> captor = ArgumentCaptor.forClass(TravelInfo.class);
        verify(travelInfoMapper).insertTravelInfo(captor.capture());
        return captor.getValue();
    }

    private List<TravelInfoTranslation> captureTranslationInserts() {
        ArgumentCaptor<TravelInfoTranslation> captor = ArgumentCaptor.forClass(TravelInfoTranslation.class);
        verify(travelInfoMapper, org.mockito.Mockito.atLeastOnce()).insertTranslation(captor.capture());
        return captor.getAllValues();
    }
}
