package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 스프링 노트의 코일은 종이 상자 밖에 얹는 장식이다.
 *
 * <p>종이 비율·여백·안쪽 단위를 건드리지 않으므로 붙여 둔 사진/스티커/라벨의
 * 상대좌표와 크기, 회전은 일반 노트와 똑같이 남는다.
 */
class DiarySpiralNotebookUiContractTest {

    private static final Path DIARY_CSS = Path.of("src/main/resources/static/css/diary.css");
    private static final Path DETAIL_HTML =
            Path.of("src/main/resources/templates/diary/detail.html");
    private static final Path SPRING_DIR =
            Path.of("src/main/resources/static/images/diary/notebook");

    @Test
    void bothModesGetTheirShapeFromTheOneStoredValue() throws IOException {
        String template = read(DETAIL_HTML);

        // 읽기의 펼침과 편집의 한 장이 같은 값을 쓴다
        assertThat(template).contains("class=\"diary-book-spread\" th:classappend=\"${notebookClass}\"");
        assertThat(template).contains("class=\"diary-book-single\" th:if=\"${editMode}\""
                + " th:classappend=\"${notebookClass}\"");
    }

    /** 펼침 조각(readBoard)이 통째로 갈려도 모양과 코일이 그대로 따라온다. */
    @Test
    void theCoilLivesInsideThePartThatIsSwappedOnAPageTurn() throws IOException {
        String template = read(DETAIL_HTML);

        String board = between(template, "th:fragment=\"readBoard\"", "</section>");
        assertThat(board).contains("th:classappend=\"${notebookClass}\"");
        assertThat(board).contains("class=\"diary-book-spring\" th:if=\"${spiralNotebook}\"");
    }

    @Test
    void aClassicNotebookNeverRendersACoil() throws IOException {
        String template = read(DETAIL_HTML);

        // 코일은 스프링일 때만 그려진다. 일반 노트의 화면에는 아예 나오지 않는다
        for (String coil : new String[]{"diary-book-spring", "diary-sheet-spring"}) {
            int at = template.indexOf("class=\"" + coil + "\"");
            while (at >= 0) {
                assertThat(template.substring(at, at + 120)).as("%s", coil)
                        .contains("th:if=\"${spiralNotebook}\"");
                at = template.indexOf("class=\"" + coil + "\"", at + 1);
            }
        }
        // 두 장 사이의 칸은 두 모양이 함께 쓴다. 안에 든 장식은 없다
        assertThat(template).contains("class=\"diary-book-gutter\" aria-hidden=\"true\"></div>");
    }

    @Test
    void theCoilIsDrawnWithARepeatedPictureNotAMask() throws IOException {
        String css = read(DIARY_CSS);
        String spread = rule(css, ".diary-book-spring");
        String sheet = rule(css, ".diary-sheet-spring");

        for (String coil : new String[]{spread, sheet}) {
            assertThat(coil).contains("background-image: url('/images/diary/notebook/");
            assertThat(coil).contains("background-repeat: repeat-y;");
            assertThat(coil).doesNotContain("mask");
            // 장식일 뿐이라 클릭/드래그를 가로채지 않는다
            assertThat(coil).contains("pointer-events: none;");
            // 크기가 모두 % 라 확대/축소해도 고리 모양과 개수가 그대로다
            assertThat(coil).contains("background-size: 100% 7%;");
        }
        /*
          폭은 두 화면 모두 "한 장" 기준이다.
          펼침 쪽은 제본선(26px)이 고정이라 50% - 13px 로 한 장 폭을 되돌려 쓴다.
          펼침 %로 재면 화면이 넓어질수록 구멍과 철사가 벌어진다.
        */
        assertThat(spread).contains("width: calc(28.5px + (50% - 13px) * 0.0329);");
        assertThat(sheet).contains("width: 7.74%;");
        // 그림 한 장으로 두 화면을 모두 그린다
        assertThat(sheet).contains("spring-ring.svg");
    }

    /**
     * 구멍은 종이가 그리고 철사는 그 구멍을 잇는다.
     *
     * <p>둘 다 종이 위쪽 끝에서 같은 간격으로 되풀이되므로 고리마다 구멍 하나가 맞물린다.
     */
    @Test
    void thePaperCarriesTheHolesAndTheWireJoinsThem() throws IOException {
        String css = read(DIARY_CSS);
        String holes = rule(css, ".diary-book-spiral .diary-sheet::after");

        // 코일과 같은 간격, 같은 시작점
        assertThat(holes).contains("background-size: 100% 7%;").contains("top: 0;");
        assertThat(holes).contains("background-repeat: repeat-y;");
        // 사진/스티커(2층)보다 아래에 있는 종이의 일부다
        assertThat(holes).contains("z-index: 1;");
        assertThat(holes).contains("pointer-events: none;");
        // 종이색이 파인 정도. 검은 점이 아니고, 코일보다 눈에 띄지도 않는다
        assertThat(holes).contains("rgba(103, 88, 68, 0.55)");
        assertThat(holes).contains("ellipse 10.3% 12.7% at 50% 50%");
        // 구멍 아래쪽에만 걸치는 옅은 빛 한 층이 파인 깊이를 만든다
        assertThat(holes).contains("ellipse 10.3% 12.7% at 50% 58%");
        assertThat(holes.indexOf("rgba(255, 255, 255, 0.7)"))
                .as("빛이 구멍보다 위층이어야 아래쪽 테두리로 보인다")
                .isLessThan(holes.indexOf("rgba(103, 88, 68, 0.55)"));

        /*
          어느 화면에서든 구멍 자리는 "그 장 안쪽 끝에서 종이 폭의 2.07%"다.
          구멍 줄 가운데가 구멍이므로 폭의 절반(4.91%)에서 그만큼을 뺀 2.84% 를 넘겨 놓는다.
          세 자리가 같은 값을 쓰는 것이 곧 읽기/편집/좁은 화면이 같아진다는 뜻이다.
        */
        assertThat(rule(css, ".diary-book-spread.diary-book-spiral .diary-sheet-left::after"))
                .contains("right: -2.84%;");
        assertThat(rule(css, ".diary-book-spread.diary-book-spiral .diary-sheet-right::after"))
                .contains("left: -2.84%;");
        assertThat(rule(css, ".diary-book-single.diary-book-spiral .diary-sheet::after"))
                .contains("left: -2.84%;");
    }

    /**
     * 구멍과 철사가 같은 자를 쓴다.
     *
     * <p>구멍은 종이 폭의 %, 코일은 펼침 폭의 % 였을 때는 가운데 제본선(26px)이 고정이라
     * 화면이 넓어질수록 둘이 벌어졌다. 두 값 모두 한 장 폭에서 계산해 그 어긋남을 없앤다.
     */
    @Test
    void theHoleAndTheWireAreMeasuredFromTheSameSheet() throws IOException {
        String css = read(DIARY_CSS);

        // 펼침 폭을 그대로 쓰지 않는다. 제본선을 빼서 한 장 폭으로 되돌린다
        assertThat(rule(css, ".diary-book-spring")).contains("(50% - 13px)");
        // 한 장 화면은 처음부터 종이 폭이 기준이라 그대로 % 만 쓴다
        assertThat(rule(css, ".diary-sheet-spring"))
                .contains("left: -5.9%;")
                .doesNotContain("px)");

        // 좁은 화면에서 구멍이 왼쪽으로 옮겨 갈 때도 넘김 값은 같다
        String narrow = between(css, "@media (max-width: 860px) {", "@media (max-width: 780px) {");
        assertThat(narrow).contains("left: -2.84%;");
    }

    /**
     * 철사는 한 줄이 아니라 두 가닥이다.
     *
     * <p>실제 wire-o 처럼 같은 고리를 반 칸 어긋나게 겹쳐, 뒤 가닥은 옅게 두고
     * 앞 가닥만 또렷하게 남긴다. 구멍은 종이가 그리므로 이 그림에는 없다.
     */
    @Test
    void theWireIsTwoStrandsAndNothingElse() throws IOException {
        String ring = read(SPRING_DIR.resolve("spring-ring.svg"));

        // 좌우 끝(x = 2 / 44)이 구멍 자리를 가리키므로 두 가닥의 rx 는 같다
        assertThat(ring.split("rx=\"21\" ry=\"6\"", -1)).hasSize(3);
        // 뒤 가닥은 반 칸 아래에서 옅게, 앞 가닥은 그 위에 또렷하게
        assertThat(ring).contains("cy=\"22.7\"").contains("stroke-opacity=\"0.42\"");
        assertThat(ring).contains("cy=\"20.2\"").contains("stroke-width=\"3\"");
        assertThat(ring.indexOf("cy=\"22.7\"")).isLessThan(ring.indexOf("cy=\"20.2\""));
        // 위에서 아래로 밝기가 지는 금속 결 (같은 그림 안의 그라데이션)
        assertThat(ring).contains("<linearGradient id=\"diary-spring-wire\"");
        // 구멍은 종이가 그리므로 여기에는 칠해진 도형이 없다
        assertThat(ring).doesNotContain("fill=\"#");
    }

    /** 코일이 지날 자리는 남기되, 종이 상자와 칸 나눔은 그대로 둔다. */
    @Test
    void theGapBetweenSheetsIsShownWithoutMovingThem() throws IOException {
        String css = read(DIARY_CSS);

        // 책등의 진한 그늘 대신 아주 옅은 틈으로 바꾼다
        assertThat(rule(css, ".diary-book-spiral .diary-book-gutter"))
                .contains("rgba(63, 52, 38, 0.07)")
                .doesNotContain("0.18");
        // 접힘이 아니라 잘린 종이 끝이라 그늘도 옅게만 남는다
        assertThat(rule(css, ".diary-book-spiral .diary-sheet-left"))
                .contains("inset -5px 0 8px -8px");
        // 칸 나눔(제본선 폭)은 일반 노트와 같은 값을 그대로 쓴다
        assertThat(rule(css, ".diary-book-spread"))
                .contains("grid-template-columns: minmax(0, 1fr) 26px minmax(0, 1fr);");
    }

    @Test
    void everyRingPictureIsRealXmlWithItsOwnColours() throws IOException {
        try (var files = Files.list(SPRING_DIR)) {
            var svgs = files.filter(path -> path.toString().endsWith(".svg")).toList();
            assertThat(svgs).hasSize(1);
            for (Path svg : svgs) {
                // 주석 안의 붙임표 두 개 같은 실수로 그림이 통째로 사라지지 않게 실제로 파싱해 본다
                assertThatCode(() -> DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder().parse(svg.toFile()))
                        .as("%s", svg.getFileName()).doesNotThrowAnyException();
                String source = read(svg);
                assertThat(source).as("%s", svg.getFileName())
                        .contains("preserveAspectRatio=\"none\"")
                        .contains("stroke=\"url(#diary-spring-wire)\"");
            }
        }
    }

    @Test
    void theCoilDoesNotTouchThePaperItSitsOn() throws IOException {
        String css = read(DIARY_CSS);

        // 종이 자체의 좌표계는 그대로다
        String sheet = rule(css, ".diary-sheet");
        assertThat(sheet)
                .contains("aspect-ratio: 41 / 38;")
                .contains("container-type: inline-size;")
                .contains("--diary-page-unit: max(0.87px, 100cqw / 576);")
                .contains("padding: 5.2% 5.2% 6.95%;");
        // 자유배치 층도 예전처럼 종이 전체를 덮는다
        assertThat(rule(css, ".diary-canvas")).contains("inset: 0;");
        // 스프링이라고 해서 종이 안쪽 여백이나 칸 나눔을 새로 정하지 않는다
        String spiral = between(css, ".diary-book-spread.diary-book-spiral {",
                "/* 날짜 + 한 줄 메모");
        assertThat(spiral)
                .doesNotContain("padding")
                .doesNotContain("grid-template-columns")
                .doesNotContain("--diary-page-unit");
    }

    @Test
    void theCoilMovesToTheEdgeWhenPagesAreReadOneAtATime() throws IOException {
        String css = read(DIARY_CSS);

        // 넓은 화면: 가운데 코일 하나
        assertThat(rule(css, ".diary-book-spread .diary-sheet-spring")).contains("display: none;");

        String narrow = between(css, "@media (max-width: 860px) {", "@media (max-width: 780px) {");
        // 좁은 화면: 가운데 코일을 감추고 장마다 왼쪽 가장자리로 옮긴다
        assertThat(between(narrow, ".diary-book-spring {", "}")).contains("display: none;");
        assertThat(between(narrow, ".diary-book-spread .diary-sheet-spring {", "}"))
                .contains("display: block;");
        // 좁아졌다고 본문이나 종이 크기를 새로 줄이지 않는다
        assertThat(narrow).doesNotContain(".diary-canvas");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** 여는 중괄호까지 포함해 그 규칙 한 덩어리만 잘라 본다. */
    private String rule(String css, String selector) {
        int start = css.indexOf("\n" + selector + " {");
        assertThat(start).as("규칙을 찾지 못했습니다: " + selector).isNotNegative();
        return css.substring(start, css.indexOf('}', start));
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("end %s", end).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
