package com.tripbora.controller.travelinfo;

import com.tripbora.config.CustomLoginSuccessHandler;
import com.tripbora.config.CustomLogoutSuccessHandler;
import com.tripbora.config.SecurityConfig;
import com.tripbora.config.i18n.I18nConfig;
import com.tripbora.config.i18n.TripBoraLocaleResolver;
import com.tripbora.dto.TravelInfoDetailDto;
import com.tripbora.model.TravelInfoContentFormat;
import com.tripbora.model.TravelInfoContentType;
import com.tripbora.model.TravelInfoScope;
import com.tripbora.repository.user.UserMapper;
import com.tripbora.service.category.InfoCategoryService;
import com.tripbora.service.category.ReferenceNameLocalizationService;
import com.tripbora.service.travelinfo.FestivalDetailService;
import com.tripbora.service.travelinfo.TravelInfoService;
import com.tripbora.service.travelinfo.structured.StructuredBlock;
import com.tripbora.service.travelinfo.structured.StructuredContent;
import com.tripbora.service.travelinfo.structured.StructuredContentSamples;
import com.tripbora.service.travelinfo.structured.StructuredImage;
import jakarta.servlet.http.Cookie;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공개 여행정보 상세의 QUILL / STRUCTURED 분기와 구조화 블록 SSR.
 *
 * <p>Service 가 넘겨준 typed model 을 그대로 그리는지만 본다. (언어 바꾸기·검사는 Service 쪽 테스트가 본다)
 */
@WebMvcTest(TravelInfoController.class)
@Import({SecurityConfig.class, I18nConfig.class})
class TravelInfoStructuredDetailRenderingTest {

    @MockitoBean private TravelInfoService travelInfoService;
    @MockitoBean private FestivalDetailService festivalDetailService;
    @MockitoBean private InfoCategoryService infoCategoryService;
    @MockitoBean private ReferenceNameLocalizationService referenceNameLocalizationService;
    @MockitoBean private CustomLoginSuccessHandler customLoginSuccessHandler;
    @MockitoBean private CustomLogoutSuccessHandler customLogoutSuccessHandler;
    @MockitoBean private UserMapper userMapper;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void structuredArticleRendersBlocksInsteadOfTheDerivedSearchHtml() throws Exception {
        Document document = render(structured(StructuredContentSamples.palaceTour()), "ko");

        Element content = document.selectFirst(".travel-info-structured .structured-content");
        assertThat(content).isNotNull();
        // 파생 HTML 은 검색/SEO 용이다. 본문으로 그리지 않는다.
        assertThat(document.text()).doesNotContain("DERIVED-SEARCH-HTML");
        assertThat(document.select(".rich-text-content")).isEmpty();
        assertThat(content.children().stream().map(Element::className).toList()).containsExactly(
                "structured-section-title", "structured-text", "structured-figure",
                "structured-image-text", "structured-slider", "structured-callout",
                "structured-section-title", "structured-image-text is-image-right",
                "structured-slider", "structured-slider",
                "structured-grid is-two", "structured-grid is-three");
        assertThat(document.select("link[href^=/css/travel-info-structured.css]")).hasSize(1);
        assertThat(document.select("script[src^=/js/structured-slider.js]")).hasSize(1);
        assertThat(document.select("link[href^=/css/quill-content.css]")).hasSize(1);
    }

    @Test
    void sectionTitleTextAndCalloutUseHeadingsParagraphsAndNotes() throws Exception {
        Document document = render(structured(StructuredContentSamples.palaceTour()), "ko");

        Elements sections = document.select(".structured-section-title");
        assertThat(sections.select("h2").eachText()).containsExactly("경복궁", "창덕궁");
        assertThat(sections.first().id()).isEqualTo("structured-gyeongbok-intro");
        // 섹션 제목은 글 안의 소제목이다. 제목만 그리고 짧은 소개(lead)는 그리지 않는다.
        assertThat(sections).allMatch(section -> section.children().size() == 1
                && section.child(0).tagName().equals("h2"));
        assertThat(document.select(".structured-lead")).isEmpty();
        assertThat(document.select(".structured-text p")).hasSize(2);
        assertThat(document.select(".structured-text p").first().wholeText())
                .isEqualTo("조선 왕조의 법궁인 경복궁은 1395년에 지어졌습니다.\n광화문에서 시작해 북쪽으로 걸어 들어갑니다.");
        assertThat(document.selectFirst(".structured-callout").attr("role")).isEqualTo("note");
        assertThat(document.selectFirst(".structured-callout").text()).isEqualTo("경복궁은 매주 화요일 휴궁합니다.");
        // 블록 제목은 페이지 제목(h1) 아래 h2 / h3 이다.
        assertThat(document.select(".structured-content h1")).isEmpty();
        assertThat(document.select(".structured-image-text h3, .structured-slider h3").eachText())
                .containsExactly("근정전", "경복궁 주요 전각", "인정전", "후원 산책");
    }

    @Test
    void userTextIsEscapedNotInterpretedAsHtml() throws Exception {
        String html = renderHtml(structured(StructuredContentSamples.palaceTour()), "ko");
        Document document = Jsoup.parse(html);

        assertThat(document.select(".structured-content script")).isEmpty();
        assertThat(document.select(".structured-text p").get(1).text())
                .contains("<script>alert('x')</script> 는 글자 그대로");
        assertThat(html).contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;");
    }

    @Test
    void imagesCarryStoredSizeAltAndLoadingHints() throws Exception {
        Document document = render(structured(StructuredContentSamples.palaceTour()), "ko");

        Element full = document.selectFirst(".structured-figure img");
        assertThat(full.attr("src")).isEqualTo(StructuredContentSamples.image(1, 2000, 1333).url());
        assertThat(full.attr("alt")).isEqualTo("북악산 아래 펼쳐진 경복궁 전경");
        assertThat(full.attr("width")).isEqualTo("2000");
        assertThat(full.attr("height")).isEqualTo("1333");
        assertThat(document.selectFirst(".structured-figure figcaption").text()).isEqualTo("북악산 아래 펼쳐진 경복궁");
        assertThat(document.selectFirst(".structured-figure").hasClass("is-portrait")).isFalse();

        Element split = document.selectFirst(".structured-image-text img");
        assertThat(split.attr("alt")).isEqualTo("근정전 정면");
        assertThat(split.attr("width")).isEqualTo("1600");
        // 세 번째 블록부터는 늦게 읽는다. 첫 화면 근처 두 블록만 바로 읽는다.
        assertThat(document.select(".structured-content img").eachAttr("loading"))
                .allMatch(value -> value.equals("lazy"));
        assertThat(document.select(".structured-content img")).allMatch(img -> img.hasAttr("width")
                && img.hasAttr("height") && !img.attr("alt").isBlank());
    }

    @Test
    void sliderHasCarouselSemanticsCaptionsBelowImagesAndHiddenControlsUntilScriptRuns() throws Exception {
        Document document = render(structured(StructuredContentSamples.palaceTour()), "ko");
        Elements sliders = document.select("[data-structured-slider]");
        assertThat(sliders).hasSize(3);

        Element halls = sliders.get(0);
        assertThat(halls.attr("aria-roledescription")).isEqualTo("carousel");
        assertThat(halls.attr("aria-labelledby")).isEqualTo("structured-gyeongbok-halls-title");
        assertThat(halls.selectFirst("#structured-gyeongbok-halls-title").text()).isEqualTo("경복궁 주요 전각");
        assertThat(halls.selectFirst("[data-slider-track]").attr("tabindex")).isEqualTo("0");
        Elements slides = halls.select("[data-slider-slide]");
        assertThat(slides).hasSize(6);
        assertThat(slides.eachAttr("aria-label")).containsExactly("1 / 6", "2 / 6", "3 / 6", "4 / 6", "5 / 6", "6 / 6");
        assertThat(slides.eachAttr("aria-roledescription")).containsOnly("slide");
        Element first = slides.first();
        assertThat(first.select("figcaption strong").text()).isEqualTo("광화문");
        assertThat(first.select("figcaption span").text()).isEqualTo("경복궁의 정문");
        // 캡션은 사진 위가 아니라 사진 다음에 온다.
        assertThat(first.selectFirst("figure").child(0).hasClass("structured-slide-media")).isTrue();
        assertThat(first.selectFirst("figure").child(1).tagName()).isEqualTo("figcaption");
        // 제목만 있는 사진, 둘 다 없는 사진
        assertThat(slides.get(2).select("figcaption span")).isEmpty();

        Element controls = halls.selectFirst("[data-slider-controls]");
        assertThat(controls.hasAttr("hidden")).isTrue();
        assertThat(controls.selectFirst("[data-slider-current]").text()).isEqualTo("01");
        assertThat(controls.selectFirst(".structured-slider-counter").text()).isEqualTo("01 / 06");
        assertThat(controls.selectFirst(".structured-slider-counter").attr("aria-live")).isEqualTo("polite");
        assertThat(controls.select("button[type=button]").eachAttr("aria-label"))
                .containsExactly("이전 사진", "다음 사진");

        Element garden = sliders.get(1);
        assertThat(garden.select("[data-slider-slide]")).hasSize(3);
        assertThat(garden.select("[data-slider-slide]").get(2).select("figcaption")).isEmpty();
        assertThat(garden.selectFirst(".structured-slider-counter").text()).isEqualTo("01 / 03");

        // 한 장짜리 슬라이더는 번호·버튼이 없고, 제목이 없으면 기본 이름으로 불린다.
        Element single = sliders.get(2);
        assertThat(single.select("[data-slider-controls]")).isEmpty();
        assertThat(single.attr("aria-label")).isEqualTo("사진 슬라이더");
        assertThat(single.hasAttr("aria-labelledby")).isFalse();
        // 슬라이더마다 id 가 달라 서로 섞이지 않는다.
        assertThat(sliders.eachAttr("id")).containsExactly(
                "structured-gyeongbok-halls", "structured-changdeok-garden", "structured-deoksu-single");
    }

    @Test
    void emptyAltFallsBackToBlockTextAndAlwaysKeepsTheAltAttribute() throws Exception {
        StructuredContent content = new StructuredContent(1, java.util.List.of(
                new StructuredBlock.FullImage("captioned", StructuredContentSamples.image(1, 1600, 1000),
                        null, "북악산 아래 경복궁"),
                new StructuredBlock.FullImage("decorative", StructuredContentSamples.image(2, 1600, 1000), null, null),
                new StructuredBlock.ImageText("split", StructuredBlock.ImagePosition.LEFT,
                        StructuredContentSamples.image(3, 1200, 900), null, "근정전", "본문"),
                new StructuredBlock.ImageSlider("halls", "경복궁 주요 전각", java.util.List.of(
                        new StructuredBlock.SliderItem("i1", StructuredContentSamples.image(4, 1800, 1200),
                                "광화문 정면", "광화문", null),
                        new StructuredBlock.SliderItem("i2", StructuredContentSamples.image(5, 1800, 1200),
                                null, "흥례문", null),
                        new StructuredBlock.SliderItem("i3", StructuredContentSamples.image(6, 1800, 1200),
                                null, null, "왕의 정전"),
                        new StructuredBlock.SliderItem("i4", StructuredContentSamples.image(7, 1800, 1200),
                                null, null, null)))));

        Document document = render(structured(content), "ko");

        Elements images = document.select(".structured-content img");
        assertThat(images).allMatch(img -> img.hasAttr("alt"));
        assertThat(images.eachAttr("alt")).containsExactly(
                "북악산 아래 경복궁", "", "근정전", "광화문 정면", "흥례문", "왕의 정전", "경복궁 주요 전각");
    }

    @Test
    void imageGridRendersTwoOrThreeFixedRatioCellsWithCaptionsBelow() throws Exception {
        Document document = render(structured(StructuredContentSamples.palaceTour()), "ko");

        Elements grids = document.select(".structured-content > ul.structured-grid");
        assertThat(grids).hasSize(2);
        Element pair = grids.get(0);
        Element trio = grids.get(1);
        assertThat(pair.id()).isEqualTo("structured-changdeok-pair");
        assertThat(pair.select("> li.structured-grid-item")).hasSize(2);
        assertThat(trio.select("> li.structured-grid-item")).hasSize(3);

        // 사진은 비율 틀(.structured-grid-media) 안에, 글은 사진 아래 figcaption 에 있을 때만 둔다.
        Element first = pair.selectFirst("li figure");
        assertThat(first.child(0).className()).isEqualTo("structured-grid-media");
        assertThat(first.selectFirst("img").attr("src")).isEqualTo(StructuredContentSamples.image(14, 1800, 1200).url());
        assertThat(first.selectFirst("img").attr("width")).isEqualTo("1800");
        assertThat(first.select("figcaption strong").text()).isEqualTo("낙선재");
        assertThat(first.select("figcaption span").text()).isEqualTo("단청을 칠하지 않은 소박한 건물");
        assertThat(pair.select("li").get(1).select("figcaption span")).isEmpty();
        assertThat(trio.select("li").get(1).select("figcaption strong")).isEmpty();

        // alt 는 슬라이더와 같은 규칙: alt → 제목 → 설명. (블록 제목은 없다)
        assertThat(trio.select("img").eachAttr("alt")).containsExactly("대한문 사진", "중화전 처마", "후원 사진");
        assertThat(grids.select("img").eachAttr("loading")).allMatch("lazy"::equals);
    }

    @Test
    void imageGridAltFallsBackToItemTextsAndAlwaysKeepsTheAttribute() throws Exception {
        StructuredImage image = StructuredContentSamples.image(1, 1600, 1200);
        StructuredContent content = new StructuredContent(1, java.util.List.of(
                new StructuredBlock.ImageGrid("grid", 3, java.util.List.of(
                        new StructuredBlock.SliderItem("a", image, null, "광화문", "정문"),
                        new StructuredBlock.SliderItem("b", image, null, null, "흥례문"),
                        new StructuredBlock.SliderItem("c", image, null, null, null)))));

        Document document = render(structured(content), "ko");

        Elements images = document.select(".structured-grid img");
        assertThat(images).allMatch(img -> img.hasAttr("alt"));
        assertThat(images.eachAttr("alt")).containsExactly("광화문", "흥례문", "");
        // 앞쪽 두 블록 안의 사진은 바로 읽는다.
        assertThat(images.eachAttr("loading")).allMatch("eager"::equals);
        assertThat(document.select(".structured-grid figcaption")).hasSize(2);
    }

    @Test
    void richTextLayoutPresetBecomesAClassAndMissingLayoutIsDefault() throws Exception {
        StructuredContent content = new StructuredContent(1, java.util.List.of(
                new StructuredBlock.RichText("legacy", null, "layout 이 없던 예전 글"),
                new StructuredBlock.RichText("wide", StructuredBlock.RichTextLayout.DEFAULT, "기본 폭"),
                new StructuredBlock.RichText("essay", StructuredBlock.RichTextLayout.FOCUSED, "집중형")));

        Document document = render(structured(content), "ko");

        assertThat(document.select(".structured-text").eachAttr("class")).containsExactly(
                "structured-text", "structured-text", "structured-text is-focused");
        assertThat(document.select(".structured-text").eachAttr("style")).allMatch(String::isEmpty);
    }

    @Test
    void sectionTitleUsesALeftAccentLineAndTextBlocksShareTheContentStartLine() throws IOException {
        String css;
        try (InputStream input = getClass().getResourceAsStream("/static/css/travel-info-structured.css")) {
            assertThat(input).isNotNull();
            css = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        // 위쪽 짧은 가로선 대신 왼쪽 세로 accent 선(3px). 카드·배경은 없다.
        assertThat(rule(css, ".structured-section-title"))
                .contains("border-left: 3px solid var(--structured-accent);")
                .doesNotContain("background");
        assertThat(css).doesNotContain(".structured-section-title::before", ".structured-lead");
        // 섹션 제목과 기본 본문은 가운데로 몰지 않고 콘텐츠 왼쪽 기준선에서 시작한다.
        assertThat(rule(css, ".structured-text"))
                .contains("max-width: var(--structured-body-width);", "text-align: left;")
                .doesNotContain("margin-inline");
        assertThat(rule(css, ".structured-text.is-focused"))
                .contains("max-width: var(--structured-focused-width);", "margin-inline: auto;");
        assertThat(css).contains("--structured-body-width: 880px;", "--structured-focused-width: 700px;");
    }

    @Test
    void blockRulesLeaveTheGapBetweenBlocksToTheSiblingSpacingRules() throws IOException {
        String css;
        try (InputStream input = getClass().getResourceAsStream("/static/css/travel-info-structured.css")) {
            assertThat(input).isNotNull();
            css = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        // 블록 규칙이 자기 위아래 margin 을 정하면(예: margin: 0) "앞 블록 + 다음 블록" 여백을 덮어써 블록이 붙는다.
        for (String block : java.util.List.of(".structured-section-title", ".structured-text", ".structured-figure",
                ".structured-image-text", ".structured-grid", ".structured-callout")) {
            assertThat(rule(css, block)).as(block)
                    .doesNotContain("margin:", "margin-top", "margin-bottom", "margin-block");
        }
        assertThat(css).contains(
                ".structured-content > * + * {\n    margin-top: var(--structured-space-media);",
                "margin-top: var(--structured-space-media-pair);");
    }

    /** 미디어 쿼리 밖의 첫 규칙 본문. */
    private String rule(String css, String selector) {
        int start = css.indexOf("\n" + selector + " {");
        assertThat(start).as(selector).isNotNegative();
        return css.substring(start, css.indexOf('}', start));
    }

    @Test
    void altTextIsEscapedInsideTheAttribute() throws Exception {
        StructuredContent content = new StructuredContent(1, java.util.List.of(
                new StructuredBlock.FullImage("a", StructuredContentSamples.image(1, 1600, 1000),
                        "\"광화문\" <script>x</script>", null)));

        String html = renderHtml(structured(content), "ko");

        assertThat(html).contains("alt=\"&quot;광화문&quot; &lt;script&gt;x&lt;/script&gt;\"");
        assertThat(Jsoup.parse(html).selectFirst(".structured-figure img").attr("alt"))
                .isEqualTo("\"광화문\" <script>x</script>");
    }

    @Test
    void localizedModelFromTheServiceIsRenderedAsIs() throws Exception {
        TravelInfoDetailDto detail = structured(StructuredContentSamples.palaceTourInEnglish());
        detail.setTitle("Seoul palace tour");

        Document document = render(detail, "en");

        assertThat(document.select(".structured-section-title h2").eachText())
                .containsExactly("Gyeongbokgung", "창덕궁");
        // 예전 번역에 남은 섹션 제목 lead 는 읽기만 하고 그리지 않는다.
        assertThat(document.select(".structured-lead")).isEmpty();
        assertThat(document.text()).doesNotContain("The main royal palace of Joseon");
        assertThat(document.selectFirst(".structured-figure img").attr("alt")).isEqualTo("Gyeongbokgung below Bugaksan");
        assertThat(document.selectFirst(".structured-figure img").attr("src"))
                .isEqualTo(StructuredContentSamples.image(1, 2000, 1333).url());
        assertThat(document.selectFirst("#structured-gyeongbok-halls-title").text())
                .isEqualTo("Main halls of Gyeongbokgung");
        Element first = document.selectFirst("[data-slider-slide]");
        assertThat(first.select("figcaption strong").text()).isEqualTo("Gwanghwamun");
        assertThat(first.selectFirst("img").attr("alt")).isEqualTo("Gwanghwamun gate");
        // 번역이 없는 칸은 Service 가 원문을 넣어 준 그대로다.
        assertThat(document.select("[data-slider-slide]").get(1).select("figcaption strong").text())
                .isEqualTo("흥례문");
        assertThat(document.selectFirst(".structured-callout").text()).isEqualTo("Gyeongbokgung is closed on Tuesdays.");
        assertThat(document.select(".structured-slider-buttons button").eachAttr("aria-label"))
                .startsWith("Previous photo", "Next photo");
        assertThat(document.select("[data-structured-slider]").get(2).attr("aria-label")).isEqualTo("Photo slider");
    }

    @Test
    void quillArticleKeepsTheExistingRichTextBodyWithoutStructuredAssets() throws Exception {
        TravelInfoDetailDto detail = base();
        detail.setContent("<p class=\"ql-align-center\"><img src=\"/uploads/editor/a.png\" "
                + "class=\"ql-image-size-50\" alt=\"사진\"></p>");

        String html = renderHtml(detail, "ko");
        Document document = Jsoup.parse(html);

        assertThat(html).contains("<section class=\"travel-info-detail-content rich-text-content rich-text-image-layout\"");
        assertThat(document.selectFirst(".rich-text-content img.ql-image-size-50")).isNotNull();
        assertThat(document.select(".structured-content, .travel-info-structured")).isEmpty();
        assertThat(document.select("link[href^=/css/travel-info-structured.css]")).isEmpty();
        assertThat(document.select("script[src^=/js/structured-slider.js]")).isEmpty();
    }

    @Test
    void unreadableStructuredBlocksFallBackToTheDerivedHtml() throws Exception {
        TravelInfoDetailDto detail = structured(null);
        detail.setContent("<h2>서울 궁 투어</h2><p>대신 보이는 글</p>");

        Document document = render(detail, "ko");

        assertThat(document.selectFirst(".rich-text-content").text()).isEqualTo("서울 궁 투어 대신 보이는 글");
        assertThat(document.select(".structured-content")).isEmpty();
        assertThat(document.select("script[src^=/js/structured-slider.js]")).isEmpty();
    }

    private TravelInfoDetailDto structured(StructuredContent content) {
        TravelInfoDetailDto detail = base();
        detail.setContentFormat(TravelInfoContentFormat.STRUCTURED);
        detail.setContent("<p>DERIVED-SEARCH-HTML</p>");
        detail.setStructuredContent(content);
        return detail;
    }

    private TravelInfoDetailDto base() {
        TravelInfoDetailDto detail = new TravelInfoDetailDto();
        detail.setId(10L);
        detail.setTitle("서울 궁 투어");
        detail.setScope(TravelInfoScope.DOMESTIC);
        detail.setContentType(TravelInfoContentType.GENERAL);
        detail.setCategoryId(3L);
        detail.setCategoryName("계절여행");
        detail.setViews(7);
        detail.setCreatedAt(Timestamp.valueOf("2026-08-10 09:00:00"));
        return detail;
    }

    private Document render(TravelInfoDetailDto detail, String languageTag) throws Exception {
        return Jsoup.parse(renderHtml(detail, languageTag));
    }

    private String renderHtml(TravelInfoDetailDto detail, String languageTag) throws Exception {
        when(festivalDetailService.isPublicFestival(10L)).thenReturn(false);
        when(travelInfoService.getPublicDetail(10L)).thenReturn(detail);
        return mockMvc.perform(get("/travel-info/10")
                        .cookie(new Cookie(TripBoraLocaleResolver.COOKIE_NAME, languageTag)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
