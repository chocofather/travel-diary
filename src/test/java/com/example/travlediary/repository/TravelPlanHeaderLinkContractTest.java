package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공통 헤더에서 여행계획으로 가는 길.
 *
 * <p>메뉴는 이미 있다. 새로 만들지 않고, 그 길이 끊기지 않게만 지킨다.
 * PC 와 햄버거가 같은 조각을 함께 쓰므로 한쪽만 고쳐지는 일도 막는다.
 */
class TravelPlanHeaderLinkContractTest {

    @Test
    void theMenuGoesToTheTravelPlansPage() throws IOException {
        String header = headerHtml();

        // 여행계획 메뉴는 "함께 계획하기" 하나뿐이다. 같은 뜻의 메뉴를 더 만들지 않는다
        assertThat(header).contains(
                "href=\"/travel-plans\" th:text=\"#{nav.record.planTogether}\"");
        assertThat(countOf(header, "href=\"/travel-plans\"")).isEqualTo(1);
    }

    @Test
    void thePcMenuAndTheHamburgerShareTheOneList() throws IOException {
        String header = headerHtml();

        /*
          그 링크는 recordLinks 조각 안에 있고,
          펼침 메뉴(nav-grid)와 햄버거(site-menu-nav)가 그 조각을 함께 쓴다.
          그래서 한쪽에만 반영되는 일이 없다.
        */
        assertThat(between(header, "<ul th:fragment=\"recordLinks\">", "</ul>"))
                .contains("href=\"/travel-plans\"");
        assertThat(countOf(header, "~{fragments/header :: recordLinks}")).isEqualTo(2);

        // 하나는 PC 펼침 메뉴 안, 하나는 햄버거 안이다
        assertThat(between(header, "class=\"nav-grid\"", "</nav>"))
                .contains("~{fragments/header :: recordLinks}");
        assertThat(between(header, "class=\"site-menu-nav\"", "</nav>"))
                .contains("~{fragments/header :: recordLinks}");
    }

    @Test
    void thereIsNoSeparateMenuForFinishedTrips() throws IOException {
        String header = headerHtml();

        /*
          완료된 여행은 /travel-plans 안의 한 구역이다.
          헤더에 따로 메뉴를 두지 않는다.
        */
        assertThat(header)
                .doesNotContain("완료된 여행")
                .doesNotContain("/final");
    }

    @Test
    void aFinishedTripIsReachedFromThatSamePage() throws IOException {
        // 헤더 -> /travel-plans -> 완료된 여행 카드 -> 읽기 전용 최종본
        assertThat(resource("/templates/travelplan/list.html"))
                .contains("완료된 여행")
                .contains("@{|/travel-plans/${plan.travelPlanId}/final|}");
    }

    private String headerHtml() throws IOException {
        return resource("/templates/fragments/header.html");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int countOf(String source, String needle) {
        int count = 0;
        for (int index = source.indexOf(needle); index >= 0;
             index = source.indexOf(needle, index + needle.length())) {
            count++;
        }
        return count;
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("end %s", end).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
