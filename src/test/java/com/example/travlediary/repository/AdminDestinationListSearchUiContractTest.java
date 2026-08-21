package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 여행지 목록 검색 툴바 계약.
 * 검색 input / 검색·초기화 버튼 / 전체·국내·해외 필터가 한 폼 안에서 함께 동작해야 한다.
 */
class AdminDestinationListSearchUiContractTest {

    @Test
    void searchToolbarKeepsTheKeywordAndTheScopeFilterTogether() throws IOException {
        String list = resource("/templates/admin/destinations/list.html");

        assertThat(list)
                // 여행지명 검색 input (검색 후에도 입력값 유지)
                .contains("name=\"keyword\"")
                .contains("placeholder=\"여행지명 검색")
                .contains("aria-label=\"여행지명 검색\"")
                .contains("th:value=\"${keyword}\"")
                // 검색/조회/적용 버튼 없이 입력만으로 자동 검색한다
                .doesNotContain(">검색<")
                .doesNotContain(">조회<")
                .doesNotContain(">적용<")
                .contains(">초기화<")
                .contains("th:href=\"@{/admin/destinations}\"")
                // 전체 / 국내 / 해외 필터와 현재 상태 표시
                .contains(">전체<")
                .contains(">국내<")
                .contains(">해외<")
                .contains("' active' : ''")
                .contains("name=\"type\"");
    }

    @Test
    void keywordSearchIsDebouncedAndFiltersApplyImmediately() throws IOException {
        String script = resource("/static/js/admin-destination-filter.js");

        assertThat(script)
                // 검색창만 debounce (약 300ms), timer 중첩 방지
                .contains("300")
                .contains("clearTimeout")
                .contains("setTimeout")
                // IME 한글 조합 중에는 검색하지 않는다
                .contains("compositionstart")
                .contains("compositionend")
                // Enter 는 이중 submit 없이 즉시 검색
                .contains("Enter")
                .contains("preventDefault")
                // 지역 select 는 변경 즉시 적용
                .contains("addEventListener(\"change\"")
                .contains("submitFilters");
    }

    @Test
    void changingAParentRegionDropsItsChildConditions() throws IOException {
        String script = resource("/static/js/admin-destination-filter.js");

        assertThat(script)
                // 대륙 변경 → 국가/도시 조건 제거, 국가 변경 → 도시 조건 제거
                .contains("resetSelect(countrySelect, \"- 국가 선택 -\")")
                .contains("resetSelect(citySelect, \"- 도시 선택 -\")")
                .contains("resetSelect(districtSelect, \"- 시/군/구 선택 -\")")
                // 전체/국내/해외 전환 시 다른 범위의 지역 조건도 비운다
                .contains("resetSelect(regionSelect, \"- 시/도 선택 -\")");
    }

    @Test
    void listActionsAndDeleteConfirmationStayUntouched() throws IOException {
        String list = resource("/templates/admin/destinations/list.html");

        assertThat(list)
                .contains("/images'}")
                .contains("/admin/destinations/edit/")
                .contains("/delete'}")
                .contains("정말 삭제하시겠습니까?")
                // flash 메시지 영역 유지
                .contains("th:if=\"${error}\"");
    }

    @Test
    void listQueryFiltersByTheDisplayedKoreanNameWithBoundParameter() throws IOException {
        String mapper = resource("/mapper/DestinationMapper.xml");
        String select = between(mapper, "<select id=\"findByRegionIds\"", "</select>");

        assertThat(select)
                // 목록에 보여주는 이름(dt.name) 그대로 부분 검색
                .contains("<if test=\"keyword != null and keyword != ''\">")
                .contains("AND dt.name LIKE CONCAT('%', #{keyword}, '%')")
                // 초성검색도 같은 목록 쿼리에서 bind parameter 로 처리한다
                .contains("<if test=\"chosungPattern != null and chosungPattern != ''\">")
                .contains("AND dt.name REGEXP #{chosungPattern}")
                // 지역 조건과 AND 로 함께 적용된다
                .contains("WHERE d.region_id IN")
                // 문자열 이어붙이기(${}) 금지
                .doesNotContain("${keyword}")
                .doesNotContain("${chosungPattern}");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
