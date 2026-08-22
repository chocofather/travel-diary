package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** 관광명소 정보 카드: 데이터 표현 방식은 유지하고 레이아웃만 정보 그리드로 정리한 계약. */
class AttractionInfoUiContractTest {

    @Test
    void attractionInfoKeepsItsEmptyValueHandling() throws IOException {
        String block = attractionBlock();

        // 값이 없을 때 '-' 로 보여주던 기존 처리 방식을 그대로 유지한다
        assertThat(block)
                .contains("${attractionInfo.closedDays ?: '-'}")
                .contains("${attractionInfo.openingHours ?: '-'}")
                .contains("${attractionInfo.admissionFee ?: '-'}")
                .contains("${attractionInfo.contactNumber ?: '-'}")
                .contains("${attractionInfo.parkingAvailable ? '가능' : '불가'}")
                .contains("${attractionGuideWithBr}");
    }

    @Test
    void longUrlIsReplacedByALabelledLinkAndGuideMovesToItsOwnNote() throws IOException {
        String block = attractionBlock();

        // 긴 URL 을 그대로 노출하지 않는다
        assertThat(block)
                .contains("th:href=\"${attractionInfo.homepageUrl}\"")
                .contains("공식 홈페이지")
                .doesNotContain("th:text=\"${attractionInfo.homepageUrl}\"");
        // 긴 안내문은 정보 그리드 밖의 별도 영역에 둔다
        int gridEnd = block.indexOf("</dl>");
        assertThat(gridEnd).isGreaterThan(0);
        assertThat(block.indexOf("info-note")).isGreaterThan(gridEnd);
    }

    @Test
    void infoGridIsTwoColumnsOnDesktopAndOneColumnOnMobile() throws IOException {
        String css = readFile("src/main/resources/static/css/detail.css");
        String live = stripComments(css);

        // 카드 스타일은 타입별 정보 블록이 함께 쓰는 공통 class 로 정의된다
        assertThat(live)
                .contains(".type-info-block .info-card")
                .contains("grid-template-columns: repeat(2, minmax(0, 1fr))")
                .contains(".type-info-block .info-item-icon")
                .contains(".type-info-block .info-item-link")
                .contains(".type-info-block .info-note");
        // 모바일 1열 전환
        String mobile = live.substring(live.indexOf("@media (max-width: 720px)"));
        assertThat(mobile).contains("grid-template-columns: minmax(0, 1fr)");
    }

    @Test
    void detailStylesheetHasNoUnterminatedComment() throws IOException {
        String css = readFile("src/main/resources/static/css/detail.css");

        // 주석이 열린 채 남으면 뒤따르는 규칙이 통째로 죽는다
        assertThat(stripComments(css)).doesNotContain("/*");
    }

    private String attractionBlock() throws IOException {
        String detail = readFile("src/main/resources/templates/destination/detail.html");
        int start = detail.indexOf("class=\"attraction-info type-info-block\"");
        int end = detail.indexOf("<!-- 편의시설 -->", start);
        assertThat(start).isGreaterThan(0);
        assertThat(end).isGreaterThan(start);
        return detail.substring(start, end);
    }

    private String stripComments(String css) {
        Matcher matcher = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL).matcher(css);
        return matcher.replaceAll("");
    }

    private String readFile(String path) throws IOException {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}
