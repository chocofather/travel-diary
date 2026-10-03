package com.example.travlediary.service.travelinfo.structured;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이미지 2장 / 3장 배치(IMAGE_GRID). 칸 수(columns)는 2 또는 3이고, 사진 수는 언제나 칸 수와 같다.
 * 사진 한 장의 모양(이미지 필수, 제목·설명·alt 선택)은 슬라이더 사진과 같다.
 */
class StructuredImageGridTest {

    private static final String URL_A = "/uploads/travel-info/content/aaaaaaaa-1111-4222-8333-444444444444.jpg";
    private static final String URL_B = "/uploads/travel-info/content/bbbbbbbb-1111-4222-8333-444444444444.webp";
    private static final String URL_C = "/uploads/travel-info/content/cccccccc-1111-4222-8333-444444444444.png";

    private final StructuredContentService service = StructuredContentTestSupport.structuredContentService();
    private final StructuredContentParser parser = new StructuredContentParser(new StructuredContentValidator());

    @Test
    void parsesTwoAndThreeColumnGridsAndWritesCanonicalJson() {
        String submitted = document(
                grid("pair", "2", item("p1", URL_A, "{\"caption\":\"정문\",\"title\":\"광화문\",\"alt\":\"광화문 정면\"}"),
                        item("p2", URL_B, "{}")),
                grid("trio", "3", item("t1", URL_A, "{}"), item("t2", URL_B, "{\"title\":\"근정전\"}"),
                        item("t3", URL_C, "{\"alt\":\"  \"}")));

        StructuredContent content = parser.parseContent(submitted);

        StructuredBlock.ImageGrid pair = (StructuredBlock.ImageGrid) content.blocks().get(0);
        StructuredBlock.ImageGrid trio = (StructuredBlock.ImageGrid) content.blocks().get(1);
        assertThat(pair.type()).isEqualTo(StructuredBlockType.IMAGE_GRID);
        assertThat(pair.columns()).isEqualTo(2);
        assertThat(pair.items()).containsExactly(
                new StructuredBlock.SliderItem("p1", new StructuredImage(URL_A, 1600, 1200), "광화문 정면", "광화문", "정문"),
                new StructuredBlock.SliderItem("p2", new StructuredImage(URL_B, 1600, 1200), null, null, null));
        assertThat(trio.columns()).isEqualTo(3);
        assertThat(trio.items()).extracting(StructuredBlock.SliderItem::id).containsExactly("t1", "t2", "t3");

        // 키 순서는 type, id, columns, items. 빈 글 칸은 남지 않는다.
        String canonical = service.prepareContent(submitted).json();
        assertThat(canonical).startsWith("{\"version\":1,\"blocks\":["
                + "{\"type\":\"IMAGE_GRID\",\"id\":\"pair\",\"columns\":2,\"items\":["
                + "{\"id\":\"p1\",\"image\":{\"url\":\"" + URL_A + "\",\"width\":1600,\"height\":1200},"
                + "\"alt\":\"광화문 정면\",\"title\":\"광화문\",\"caption\":\"정문\"},");
        assertThat(canonical).doesNotContain("null", "\"alt\":\"  \"");
        assertThat(parser.parseContent(canonical)).isEqualTo(content);
    }

    @Test
    void columnsMustBeTwoOrThree() {
        for (String columns : List.of("1", "4", "0", "-2")) {
            assertInvalid(document(grid("g", columns, item("a", URL_A, "{}"), item("b", URL_B, "{}"))),
                    "1번째 블록(이미지 배치): 이미지 배치는 2장 또는 3장만 선택할 수 있습니다.");
        }
        // 칸 수가 없거나, 숫자가 아닌 값은 받지 않는다.
        assertInvalid(document("{\"type\":\"IMAGE_GRID\",\"id\":\"g\",\"items\":["
                        + item("a", URL_A, "{}") + "," + item("b", URL_B, "{}") + "]}"),
                "이미지 배치는 2장 또는 3장만 선택할 수 있습니다.");
        for (String columns : List.of("\"2\"", "2.0", "true", "null")) {
            assertThatThrownBy(() -> parser.parseContent(
                    document(grid("g", columns, item("a", URL_A, "{}"), item("b", URL_B, "{}")))))
                    .isInstanceOf(StructuredContentValidationException.class);
        }
    }

    @Test
    void itemCountMustMatchColumnsExactly() {
        assertInvalid(document(grid("g", "2", item("a", URL_A, "{}"), item("b", URL_B, "{}"), item("c", URL_C, "{}"))),
                "이미지 2장 배치에는 이미지가 정확히 2장 있어야 합니다.");
        assertInvalid(document(grid("g", "3", item("a", URL_A, "{}"), item("b", URL_B, "{}"))),
                "이미지 3장 배치에는 이미지가 정확히 3장 있어야 합니다.");
        assertInvalid(document(grid("g", "2")), "이미지 2장 배치에는 이미지가 정확히 2장 있어야 합니다.");
        assertInvalid(document("{\"type\":\"IMAGE_GRID\",\"id\":\"g\",\"columns\":3}"),
                "이미지 3장 배치에는 이미지가 정확히 3장 있어야 합니다.");
    }

    @Test
    void everyCellNeedsAnImageAndValidIds() {
        assertInvalid(document(grid("g", "2", item("a", URL_A, "{}"), "{\"id\":\"b\",\"title\":\"제목만\"}")),
                "1번째 블록(이미지 배치) 2번째 이미지: 이미지를 선택해 주세요.");
        assertInvalid(document(grid("g", "2", item("a", URL_A, "{}"),
                        "{\"id\":\"b\",\"image\":{\"url\":\"https://example.com/x.jpg\",\"width\":10,\"height\":10}}")),
                "2번째 이미지: 이미지 경로가 올바르지 않습니다.");
        assertInvalid(document(grid("g", "2", item("same", URL_A, "{}"), item("same", URL_B, "{}"))),
                "2번째 이미지: 이미지 식별자가 중복되었습니다.");
        assertInvalid(document(grid("g", "2", item("a b", URL_A, "{}"), item("b", URL_B, "{}"))),
                "1번째 이미지: 이미지 식별자가 올바르지 않습니다.");
    }

    @Test
    void cellTextsAreOptionalButLengthCheckedLikeSliderImages() {
        assertInvalid(document(grid("g", "2", item("a", URL_A, "{\"title\":\"" + "가".repeat(101) + "\"}"),
                        item("b", URL_B, "{}"))),
                "1번째 이미지: 이미지 제목은 100자 이하로 입력해 주세요.");
        assertInvalid(document(grid("g", "2", item("a", URL_A, "{}"),
                        item("b", URL_B, "{\"caption\":\"" + "가".repeat(301) + "\"}"))),
                "2번째 이미지: 캡션은 300자 이하로 입력해 주세요.");
        assertInvalid(document(grid("g", "2", item("a", URL_A, "{\"alt\":\"첫 줄\\n둘째 줄\"}"),
                        item("b", URL_B, "{}"))),
                "이미지 설명(alt)은 한 줄로 입력해 주세요.");
        // 블록 제목이나 모르는 키는 없다.
        assertInvalid(document("{\"type\":\"IMAGE_GRID\",\"id\":\"g\",\"columns\":2,\"title\":\"제목\",\"items\":["
                        + item("a", URL_A, "{}") + "," + item("b", URL_B, "{}") + "]}"),
                "허용하지 않는 항목이 있습니다: title");
    }

    @Test
    void gridImagesAreCollectedForFileLifecycle() {
        StructuredContent content = new StructuredContent(1, List.of(
                new StructuredBlock.FullImage("full", new StructuredImage(URL_A, 10, 10), null, null),
                new StructuredBlock.ImageGrid("grid", 3, List.of(
                        cell("c1", URL_B), cell("c2", URL_A), cell("c3", URL_C)))));

        assertThat(service.collectImageUrls(content)).containsExactly(URL_A, URL_B, URL_C);
        assertThat(new StructuredContentSerializer().write(content)).doesNotContain("\"images\"", "itemAlt");
    }

    @Test
    void derivedSearchHtmlListsCellTitlesAndCaptions() {
        StructuredContent content = new StructuredContent(1, List.of(
                new StructuredBlock.ImageGrid("grid", 3, List.of(
                        new StructuredBlock.SliderItem("a", new StructuredImage(URL_A, 10, 10), "대한문 사진", "대한문", "정문"),
                        new StructuredBlock.SliderItem("b", new StructuredImage(URL_B, 10, 10), "중화전 사진", null, null),
                        new StructuredBlock.SliderItem("c", new StructuredImage(URL_C, 10, 10), null, null, null)))));

        assertThat(new StructuredContentTextRenderer().render(content))
                .isEqualTo("<ul><li>대한문<br>정문</li><li>중화전 사진</li></ul>");
    }

    @Test
    void translationOverridesCellTextsButKeepsColumnsImagesAndOrder() {
        StructuredContent korean = new StructuredContent(1, List.of(
                new StructuredBlock.ImageGrid("grid", 2, List.of(
                        new StructuredBlock.SliderItem("a", new StructuredImage(URL_A, 10, 10), "대한문 정면", "대한문", null),
                        new StructuredBlock.SliderItem("b", new StructuredImage(URL_B, 10, 10), "돌담길 사진", null, "돌담길")))));
        String english = """
                {"blocks": {"grid": {"title": "no block title", "items": {
                  "a": {"title": "Daehanmun"},
                  "gone": {"title": "Removed cell"}
                }}}}""";

        StructuredContent localized = service.localize(korean, english);

        StructuredBlock.ImageGrid grid = (StructuredBlock.ImageGrid) localized.blocks().get(0);
        assertThat(grid.columns()).isEqualTo(2);
        assertThat(grid.images()).extracting(StructuredImage::url).containsExactly(URL_A, URL_B);
        assertThat(grid.items().get(0).title()).isEqualTo("Daehanmun");
        // 번역 제목이 있으면 한국어 alt 대신 그 언어의 글로 alt 를 채운다. 번역이 없는 칸은 원문 그대로다.
        assertThat(grid.items().stream().map(grid::itemAlt).toList()).containsExactly("Daehanmun", "돌담길 사진");
        // 저장 정리: 블록 제목(이미지 배치에는 없음)과 원문에 없는 칸의 번역은 남기지 않는다.
        StructuredContentService.PreparedText prepared = service.prepareTranslation(korean, english, "English");
        assertThat(prepared.json()).isEqualTo("{\"blocks\":{\"grid\":{\"items\":{\"a\":{\"title\":\"Daehanmun\"}}}}}");
        assertThat(new StructuredContentLocalizer().alignToBase(korean, parser.parseText(english)).blocks().get("grid"))
                .isEqualTo(new StructuredText.BlockText(null, null, null, null,
                        Map.of("a", new StructuredText.ItemText("Daehanmun", null, null))));
        assertThat(prepared.derivedContent()).isEqualTo("<ul><li>Daehanmun</li><li>돌담길</li></ul>");
    }

    @Test
    void altFallsBackToCellTitleThenCaptionThenEmpty() {
        StructuredImage image = new StructuredImage(URL_A, 10, 10);
        StructuredBlock.ImageGrid grid = new StructuredBlock.ImageGrid("g", 3, List.of(
                new StructuredBlock.SliderItem("a", image, "광화문 정면", "광화문", "정문"),
                new StructuredBlock.SliderItem("b", image, " ", "흥례문", "두 번째 문"),
                new StructuredBlock.SliderItem("c", image, null, null, "정전")));
        StructuredBlock.ImageGrid empty = new StructuredBlock.ImageGrid("e", 2, List.of(
                new StructuredBlock.SliderItem("a", image, null, null, null)));

        assertThat(grid.items().stream().map(grid::itemAlt).toList()).containsExactly("광화문 정면", "흥례문", "정전");
        assertThat(empty.itemAlt(empty.items().get(0))).isEmpty();
    }

    // ---- helpers --------------------------------------------------------------------------

    private void assertInvalid(String json, String message) {
        assertThatThrownBy(() -> parser.parseContent(json))
                .isInstanceOf(StructuredContentValidationException.class)
                .hasMessageContaining(message);
    }

    private static StructuredBlock.SliderItem cell(String id, String url) {
        return new StructuredBlock.SliderItem(id, new StructuredImage(url, 10, 10), null, null, null);
    }

    private static String document(String... blocks) {
        return "{\"version\":1,\"blocks\":[" + String.join(",", blocks) + "]}";
    }

    private static String grid(String id, String columns, String... items) {
        return "{\"type\":\"IMAGE_GRID\",\"id\":\"" + id + "\",\"columns\":" + columns
                + ",\"items\":[" + String.join(",", items) + "]}";
    }

    /** 글 칸은 extra JSON 객체로 넘긴다. ("{}" 면 이미지 뿐) */
    private static String item(String id, String url, String extra) {
        String texts = extra.substring(1, extra.length() - 1).trim();
        return "{\"id\":\"" + id + "\",\"image\":{\"url\":\"" + url + "\",\"width\":1600,\"height\":1200}"
                + (texts.isEmpty() ? "" : "," + texts) + "}";
    }
}
