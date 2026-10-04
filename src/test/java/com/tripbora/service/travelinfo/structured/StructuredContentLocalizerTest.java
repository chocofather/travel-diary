package com.tripbora.service.travelinfo.structured;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 번역 글을 덮어써도 이미지는 원문 것을 그대로 쓴다. 이미지 출처표시(credit)도 사진 자체의 정보라
 * 번역되지 않고 url·크기와 함께 그대로 남는다.
 */
class StructuredContentLocalizerTest {

    private static final String URL_A = "/uploads/travel-info/content/aaaaaaaa-1111-4222-8333-444444444444.jpg";
    private static final String URL_B = "/uploads/travel-info/content/bbbbbbbb-1111-4222-8333-444444444444.webp";

    private final StructuredContentService service = StructuredContentTestSupport.structuredContentService();

    @Test
    void translationKeepsImageUrlSizeAndCreditOnEveryImageBlock() {
        StructuredImage commons = new StructuredImage(URL_A, 1600, 1067, new StructuredImageCredit(
                "John Doe", "Wikimedia Commons", "https://commons.wikimedia.org/wiki/File:A.jpg",
                "CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/"));
        StructuredImage kto = new StructuredImage(URL_B, 1200, 800,
                new StructuredImageCredit(null, "한국관광공사", null, "공공누리 제1유형", null));
        StructuredImage plain = new StructuredImage(URL_B, 800, 600);
        StructuredContent korean = new StructuredContent(1, List.of(
                new StructuredBlock.FullImage("full", commons, "경복궁 전경", "가을의 경복궁"),
                new StructuredBlock.ImageText("split", StructuredBlock.ImagePosition.LEFT, kto,
                        "강녕전 사진", "강녕전", "왕의 침전입니다."),
                new StructuredBlock.ImageSlider("slider", "주요 전각", List.of(
                        new StructuredBlock.SliderItem("i1", kto, "강녕전 내부", "강녕전 내부", null),
                        new StructuredBlock.SliderItem("i2", plain, null, "교태전", null))),
                new StructuredBlock.ImageGrid("grid", 2, List.of(
                        new StructuredBlock.SliderItem("c1", commons, null, "광화문", null),
                        new StructuredBlock.SliderItem("c2", plain, null, null, "돌담길")))));
        String english = """
                {"blocks": {
                  "full": {"caption": "Gyeongbokgung in autumn"},
                  "split": {"title": "Gangnyeongjeon", "text": "The king's quarters."},
                  "slider": {"items": {"i1": {"title": "Inside Gangnyeongjeon"}}},
                  "grid": {"items": {"c1": {"title": "Gwanghwamun"}}}
                }}""";

        StructuredContent localized = service.localize(korean, english);

        // 번역이 실제로 적용되었다. (번역을 읽지 못해 원문을 그대로 돌려준 것이 아니다)
        StructuredBlock.FullImage full = (StructuredBlock.FullImage) localized.blocks().get(0);
        StructuredBlock.ImageText split = (StructuredBlock.ImageText) localized.blocks().get(1);
        StructuredBlock.ImageSlider slider = (StructuredBlock.ImageSlider) localized.blocks().get(2);
        StructuredBlock.ImageGrid grid = (StructuredBlock.ImageGrid) localized.blocks().get(3);
        assertThat(full.caption()).isEqualTo("Gyeongbokgung in autumn");
        assertThat(split.title()).isEqualTo("Gangnyeongjeon");
        assertThat(slider.items().get(0).title()).isEqualTo("Inside Gangnyeongjeon");
        assertThat(grid.items().get(0).title()).isEqualTo("Gwanghwamun");

        // 이미지(url · width · height · credit 전체)는 블록마다 원문과 같다.
        assertThat(full.image()).isEqualTo(commons);
        assertThat(split.image()).isEqualTo(kto);
        assertThat(slider.images()).containsExactly(kto, plain);
        assertThat(grid.images()).containsExactly(commons, plain);
        assertThat(full.image().credit().author()).isEqualTo("John Doe");
        assertThat(slider.items().get(1).image().credit()).isNull();
    }
}
