package com.example.travlediary.service.travelinfo.structured;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이미지 설명(alt)은 선택 입력이다. 화면 img 의 alt 는 언제나 쓰되, 비었으면 블록의 다른 글로 대신 채운다.
 * <ul>
 *   <li>큰 이미지: alt → 캡션 → ""</li>
 *   <li>이미지 + 글: alt → 제목 → ""</li>
 *   <li>슬라이더 사진: alt → 사진 제목 → 사진 설명 → 슬라이더 제목 → ""</li>
 * </ul>
 * 번역 화면에서는 번역 alt 가 먼저이고, 없으면 그 이미지의 번역 글이 원문(한국어) alt 보다 먼저다.
 */
class StructuredImageAltFallbackTest {

    private static final StructuredImage IMAGE = StructuredContentSamples.image(1, 1200, 800);

    @Test
    void fullImageUsesAltThenCaptionThenEmpty() {
        assertThat(new StructuredBlock.FullImage("a", IMAGE, "경복궁 전경", "캡션").effectiveAlt()).isEqualTo("경복궁 전경");
        assertThat(new StructuredBlock.FullImage("a", IMAGE, " ", "북악산 아래 경복궁").effectiveAlt())
                .isEqualTo("북악산 아래 경복궁");
        assertThat(new StructuredBlock.FullImage("a", IMAGE, null, null).effectiveAlt()).isEmpty();
    }

    @Test
    void imageTextUsesAltThenTitleThenEmpty() {
        assertThat(imageText("근정전 정면", "근정전").effectiveAlt()).isEqualTo("근정전 정면");
        assertThat(imageText(null, "근정전").effectiveAlt()).isEqualTo("근정전");
        assertThat(imageText(null, null).effectiveAlt()).isEmpty();
    }

    @Test
    void sliderItemUsesAltThenTitleThenCaptionThenSliderTitleThenEmpty() {
        StructuredBlock.ImageSlider slider = new StructuredBlock.ImageSlider("s", "경복궁 주요 전각", List.of(
                item("i1", "광화문 정면", "광화문", "정문"),
                item("i2", null, "흥례문", "두 번째 문"),
                item("i3", null, null, "왕의 정전"),
                item("i4", null, null, null)));
        StructuredBlock.ImageSlider untitled = new StructuredBlock.ImageSlider("t", null, List.of(item("i1", null, null, null)));

        assertThat(slider.items().stream().map(slider::itemAlt).toList())
                .containsExactly("광화문 정면", "흥례문", "왕의 정전", "경복궁 주요 전각");
        assertThat(untitled.itemAlt(untitled.items().get(0))).isEmpty();
    }

    @Test
    void localizedAltPrefersTranslatedAltThenTranslatedTextsOverTheKoreanAlt() {
        StructuredContent korean = new StructuredContent(1, List.of(
                new StructuredBlock.FullImage("full", IMAGE, "경복궁 전경", "북악산 아래 경복궁"),
                new StructuredBlock.ImageText("split", StructuredBlock.ImagePosition.LEFT, IMAGE,
                        "근정전 정면", "근정전", "본문"),
                new StructuredBlock.ImageSlider("halls", "경복궁 주요 전각", List.of(
                        item("translated-alt", "광화문 정면", "광화문", null),
                        item("translated-title", "흥례문 정면", "흥례문", null),
                        item("untranslated", "근정전 정면", "근정전", null),
                        item("empty", null, null, null)))));

        StructuredContent english = StructuredContentTestSupport.structuredContentService().localize(korean, """
                {"blocks": {
                  "full": {"caption": "Gyeongbokgung below Bugaksan"},
                  "split": {"title": "Geunjeongjeon"},
                  "halls": {"title": "Main halls", "items": {
                    "translated-alt": {"alt": "Front of Gwanghwamun", "title": "Gwanghwamun"},
                    "translated-title": {"title": "Heungnyemun"}
                  }}
                }}""");

        // 번역 캡션·제목이 있으면 한국어 alt 대신 그 언어의 글을 쓴다.
        assertThat(((StructuredBlock.FullImage) english.blocks().get(0)).effectiveAlt())
                .isEqualTo("Gyeongbokgung below Bugaksan");
        assertThat(((StructuredBlock.ImageText) english.blocks().get(1)).effectiveAlt()).isEqualTo("Geunjeongjeon");
        StructuredBlock.ImageSlider slider = (StructuredBlock.ImageSlider) english.blocks().get(2);
        assertThat(slider.items().stream().map(slider::itemAlt).toList()).containsExactly(
                "Front of Gwanghwamun", // 번역 alt
                "Heungnyemun",          // 번역 제목
                "근정전 정면",           // 번역이 없는 사진은 원문 alt
                "Main halls");          // 아무 글도 없으면 번역된 슬라이더 제목
    }

    private StructuredBlock.ImageText imageText(String alt, String title) {
        return new StructuredBlock.ImageText("split", StructuredBlock.ImagePosition.LEFT, IMAGE, alt, title, "본문");
    }

    private StructuredBlock.SliderItem item(String id, String alt, String title, String caption) {
        return new StructuredBlock.SliderItem(id, IMAGE, alt, title, caption);
    }
}
