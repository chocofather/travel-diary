package com.example.travlediary.service.travelinfo.structured;

import com.example.travlediary.service.travelinfo.TravelInfoContent;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredContentTextRendererTest {

    private static final StructuredImage IMAGE = new StructuredImage(
            "/uploads/travel-info/content/123e4567-e89b-12d3-a456-426614174000.webp", 2000, 1333);

    private final StructuredContentTextRenderer renderer = new StructuredContentTextRenderer();

    @Test
    void rendersTextOfEveryBlockAsSimpleHtmlWithoutImages() {
        String html = renderer.render(content(
                new StructuredBlock.SectionTitle("intro", "서울 궁 투어"),
                new StructuredBlock.RichText("text", StructuredBlock.RichTextLayout.FOCUSED,
                        "첫 문단 첫 줄\n첫 문단 둘째 줄\n\n\n둘째 문단"),
                new StructuredBlock.FullImage("gate", IMAGE, "광화문 전경", "경복궁의 정문"),
                new StructuredBlock.ImageText("palace", StructuredBlock.ImagePosition.LEFT, IMAGE,
                        "근정전 사진", "근정전", "조선의 법궁"),
                new StructuredBlock.ImageSlider("gyeongbok", "경복궁 주요 전각", List.of(
                        new StructuredBlock.SliderItem("i1", IMAGE, "광화문 사진", "광화문", "경복궁의 정문"),
                        new StructuredBlock.SliderItem("i2", IMAGE, "흥례문 사진", "흥례문", null),
                        new StructuredBlock.SliderItem("i3", IMAGE, "근정전 사진", null, "정전"),
                        new StructuredBlock.SliderItem("i4", IMAGE, "경회루 사진", null, null))),
                new StructuredBlock.Callout("tip", "월요일은 휴궁입니다.")));

        assertThat(html).isEqualTo(
                "<h2>서울 궁 투어</h2>"
                        + "<p>첫 문단 첫 줄<br>첫 문단 둘째 줄</p><p>둘째 문단</p>"
                        + "<p>경복궁의 정문</p>"
                        + "<h3>근정전</h3><p>조선의 법궁</p>"
                        + "<h3>경복궁 주요 전각</h3>"
                        + "<ul><li>광화문<br>경복궁의 정문</li><li>흥례문</li><li>정전</li><li>경회루 사진</li></ul>"
                        + "<p>월요일은 휴궁입니다.</p>");
        assertThat(html).doesNotContain("<img", "/uploads/");
    }

    @Test
    void optionalTextsAreSkippedAndImageWithoutCaptionKeepsAlt() {
        String html = renderer.render(content(
                new StructuredBlock.SectionTitle("intro", "제목"),
                new StructuredBlock.FullImage("gate", IMAGE, "광화문 전경", " "),
                new StructuredBlock.ImageText("palace", StructuredBlock.ImagePosition.RIGHT, IMAGE,
                        "사진", null, "본문"),
                new StructuredBlock.ImageSlider("s", null, List.of(
                        new StructuredBlock.SliderItem("i1", IMAGE, "사진 설명", null, null)))));

        assertThat(html).isEqualTo(
                "<h2>제목</h2><p>광화문 전경</p><p>본문</p><ul><li>사진 설명</li></ul>");
    }

    @Test
    void escapesUserTextSoItCannotBecomeMarkup() {
        String html = renderer.render(content(
                new StructuredBlock.SectionTitle("a", "<script>alert(1)</script>"),
                new StructuredBlock.Callout("q", "\"인용\" & '작은따옴표'"),
                new StructuredBlock.RichText("b", "<img src=x onerror=alert(1)>\n<b>굵게</b>"),
                new StructuredBlock.Callout("c", "&lt;이미 escape 된 글&gt;")));

        assertThat(html).isEqualTo(
                "<h2>&lt;script&gt;alert(1)&lt;/script&gt;</h2>"
                        + "<p>&quot;인용&quot; &amp; &#39;작은따옴표&#39;</p>"
                        + "<p>&lt;img src=x onerror=alert(1)&gt;<br>&lt;b&gt;굵게&lt;/b&gt;</p>"
                        + "<p>&amp;lt;이미 escape 된 글&amp;gt;</p>");
        assertThat(Jsoup.parseBodyFragment(html).select("script, img, b")).isEmpty();
    }

    @Test
    void fourByteCharactersBecomeNumericReferencesForTheUtf8mb3ContentColumn() {
        String html = renderer.render(content(
                new StructuredBlock.SectionTitle("a", "궁 투어 😀"),
                new StructuredBlock.Callout("b", "𠀀 한자")));

        assertThat(html).isEqualTo("<h2>궁 투어 &#x1F600;</h2><p>&#x20000; 한자</p>");
        // utf8mb3 는 문자당 3바이트까지만 받는다. 결과에 4바이트 UTF-8 문자가 없어야 한다.
        assertThat(html.codePoints().noneMatch(Character::isSupplementaryCodePoint)).isTrue();
        // 검색/SEO 가 읽는 텍스트로는 원래 문자가 돌아온다.
        assertThat(Jsoup.parseBodyFragment(html).text()).isEqualTo("궁 투어 😀 𠀀 한자");
    }

    @Test
    void unpairedSurrogatesAreReplacedInsteadOfBreakingStorage() {
        assertThat(StructuredContentTextRenderer.escapeHtml("a\uD800b")).isEqualTo("a�b");
    }

    @Test
    void renderedHtmlCountsAsContentForExistingHasContentAndSearchText() {
        String html = renderer.render(content(
                new StructuredBlock.FullImage("only-image", IMAGE, "경복궁 전경", null)));

        assertThat(TravelInfoContent.hasContent(html)).isTrue();
        assertThat(Jsoup.parseBodyFragment(html).text()).isEqualTo("경복궁 전경");
    }

    private StructuredContent content(StructuredBlock... blocks) {
        return new StructuredContent(StructuredContent.CURRENT_VERSION, List.of(blocks));
    }
}
