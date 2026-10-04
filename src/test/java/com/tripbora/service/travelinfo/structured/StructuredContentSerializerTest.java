package com.tripbora.service.travelinfo.structured;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredContentSerializerTest {

    private static final StructuredImage IMAGE = new StructuredImage(
            "/uploads/travel-info/content/123e4567-e89b-12d3-a456-426614174000.png", 1200, 800);

    private final StructuredContentParser parser =
            new StructuredContentParser(new StructuredContentValidator());
    private final StructuredContentSerializer serializer = new StructuredContentSerializer();

    @Test
    void canonicalContentJsonIsReadBackToTheSameModel() {
        StructuredContent content = new StructuredContent(1, List.of(
                new StructuredBlock.SectionTitle("intro", "제목"),
                new StructuredBlock.RichText("text", "첫 줄\n\n둘째 문단"),
                new StructuredBlock.RichText("focused", StructuredBlock.RichTextLayout.FOCUSED, "집중형"),
                new StructuredBlock.FullImage("image", IMAGE, "설명", "캡션"),
                new StructuredBlock.ImageText("split", StructuredBlock.ImagePosition.RIGHT, IMAGE,
                        "설명", "제목", "본문"),
                new StructuredBlock.ImageSlider("slider", null, List.of(
                        new StructuredBlock.SliderItem("i1", IMAGE, "설명", null, "캡션"))),
                new StructuredBlock.Callout("callout", "강조")));

        String json = serializer.write(content);

        assertThat(parser.parseContent(json)).isEqualTo(content);
        assertThat(serializer.write(parser.parseContent(json))).isEqualTo(json);
        // 값이 없는 칸과 record 의 보조 메서드는 JSON 에 남지 않는다.
        assertThat(json).doesNotContain("null", "\"lead\"", "hasOverrides", "\"empty\"");
        assertThat(json).startsWith("{\"version\":1,\"blocks\":[{\"type\":\"SECTION_TITLE\",\"id\":\"intro\"");
    }

    @Test
    void imageCreditRoundTripsAndMissingCreditIsNotWritten() {
        StructuredImage credited = new StructuredImage(IMAGE.url(), 1200, 800, new StructuredImageCredit(
                "John Doe", "Wikimedia Commons", "https://commons.wikimedia.org/wiki/File:A.jpg",
                "CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/"));
        StructuredImage sourceOnly = new StructuredImage(IMAGE.url(), 1200, 800,
                new StructuredImageCredit(null, "한국관광공사", null, "공공누리 제1유형", null));
        StructuredContent content = new StructuredContent(1, List.of(
                new StructuredBlock.FullImage("full", credited, "설명", null),
                new StructuredBlock.ImageGrid("grid", 2, List.of(
                        new StructuredBlock.SliderItem("c1", sourceOnly, "설명", null, null),
                        new StructuredBlock.SliderItem("c2", IMAGE, "설명", null, null)))));

        String json = serializer.write(content);

        assertThat(json).contains(
                "\"image\":{\"url\":\"" + IMAGE.url() + "\",\"width\":1200,\"height\":800,\"credit\":{"
                        + "\"author\":\"John Doe\",\"source\":\"Wikimedia Commons\","
                        + "\"sourceUrl\":\"https://commons.wikimedia.org/wiki/File:A.jpg\","
                        + "\"license\":\"CC BY-SA 4.0\","
                        + "\"licenseUrl\":\"https://creativecommons.org/licenses/by-sa/4.0/\"}}",
                "\"credit\":{\"source\":\"한국관광공사\",\"license\":\"공공누리 제1유형\"}",
                "{\"id\":\"c2\",\"image\":{\"url\":\"" + IMAGE.url() + "\",\"width\":1200,\"height\":800},");
        assertThat(json).doesNotContain("null");
        // 공개 화면용 보조 메서드(출처표시 조각)는 JSON 에 남지 않고, JSON 키로 들어와도 받지 않는다.
        assertThat(json).doesNotContain("displayParts", "creditParts");
        assertThatThrownBy(() -> parser.parseContent(json.replace("\"credit\":{\"author\"",
                "\"credit\":{\"displayParts\":[],\"author\"")))
                .hasMessageContaining("허용하지 않는 항목이 있습니다: displayParts");
        assertThat(parser.parseContent(json)).isEqualTo(content);
        assertThat(serializer.write(parser.parseContent(json))).isEqualTo(json);
    }

    @Test
    void storedJsonWithoutCreditIsRewrittenUnchanged() {
        String stored = "{\"version\":1,\"blocks\":["
                + "{\"type\":\"FULL_IMAGE\",\"id\":\"full\",\"image\":{\"url\":\"" + IMAGE.url()
                + "\",\"width\":1200,\"height\":800},\"alt\":\"설명\",\"caption\":\"캡션\"},"
                + "{\"type\":\"IMAGE_SLIDER\",\"id\":\"slider\",\"items\":[{\"id\":\"i1\",\"image\":{\"url\":\""
                + IMAGE.url() + "\",\"width\":1200,\"height\":800},\"title\":\"제목\"}]}]}";

        assertThat(serializer.write(parser.parseContent(stored))).isEqualTo(stored);
    }

    @Test
    void canonicalTextJsonSortsKeysOmitsEmptyValuesAndRoundTrips() {
        StructuredText text = new StructuredText(Map.of(
                "zeta", new StructuredText.BlockText(null, "Z", null, null, Map.of()),
                "alpha", new StructuredText.BlockText("A", "  ", null, null, Map.of(
                        "i2", new StructuredText.ItemText(null, "two", null),
                        "i1", new StructuredText.ItemText("one", null, null)))));

        String json = serializer.write(text);

        assertThat(json).isEqualTo("{\"blocks\":{"
                + "\"alpha\":{\"title\":\"A\",\"items\":{\"i1\":{\"title\":\"one\"},\"i2\":{\"caption\":\"two\"}}},"
                + "\"zeta\":{\"text\":\"Z\"}}}");
        assertThat(parser.parseText(json)).isEqualTo(text);
    }

    @Test
    void helperMethodNamesAreNotAcceptedAsJsonFields() {
        assertThatThrownBy(() -> parser.parseText("{\"blocks\":{\"a\":{\"title\":\"T\",\"hasOverrides\":true}}}"))
                .hasMessageContaining("허용하지 않는 항목이 있습니다: hasOverrides");
        assertThatThrownBy(() -> parser.parseText("{\"blocks\":{\"a\":{\"title\":\"T\",\"empty\":false}}}"))
                .hasMessageContaining("허용하지 않는 항목이 있습니다: empty");
    }
}
