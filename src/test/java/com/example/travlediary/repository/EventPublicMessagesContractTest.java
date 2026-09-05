package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공개 이벤트 목록·상세 화면의 고정 문구가 messages 로 옮겨졌는지 본다.
 *
 * <p>제목·상세 내용·인포그래픽 포스터처럼 event_translations 에서 언어별로 오는 값은
 * messages 대상이 아니다. 관리자 화면(/admin/**)도 한국어 고정이라 대상이 아니다.
 */
class EventPublicMessagesContractTest {

    private static final String LIST = "/templates/event/event-list.html";
    private static final String DETAIL = "/templates/event/event-detail.html";

    /** 공개 이벤트 화면이 쓰는 키. 여섯 번들에 모두 있어야 한다. */
    private static final List<String> KEYS = List.of(
            "event.kicker",
            "event.imageAlt",
            "event.status.ongoing", "event.status.upcoming", "event.status.ended",
            "event.list.title", "event.list.description", "event.list.tabs",
            "event.list.empty.ongoing", "event.list.empty.upcoming", "event.list.empty.ended",
            "event.list.pagination",
            "event.list.pagination.previous", "event.list.pagination.next",
            "event.detail.totalDays", "event.detail.startsIn", "event.detail.endsIn",
            "event.detail.todayEnds",
            "event.detail.posterAlt", "event.detail.section.title", "event.detail.back");

    @ParameterizedTest
    @ValueSource(strings = {"", "_ko", "_en", "_ja", "_zh_CN", "_zh_TW"})
    void everyBundleCarriesEveryEventKey(String suffix) throws IOException {
        Properties bundle = bundle("/messages" + suffix + ".properties");

        for (String key : KEYS) {
            assertThat(bundle.getProperty(key)).as("%s in messages%s", key, suffix)
                    .isNotNull().isNotBlank();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"_en", "_ja", "_zh_CN", "_zh_TW"})
    void translatedBundlesDoNotJustRepeatTheKoreanLabels(String suffix) throws IOException {
        Properties korean = bundle("/messages_ko.properties");
        Properties translated = bundle("/messages" + suffix + ".properties");

        // 화면에서 바로 눈에 띄는 문구는 실제로 번역돼 있어야 한다.
        for (String key : List.of("event.list.title", "event.list.description", "event.list.tabs",
                "event.status.ongoing", "event.status.upcoming", "event.status.ended",
                "event.list.empty.ongoing", "event.list.empty.upcoming", "event.list.empty.ended",
                "event.list.pagination.previous", "event.list.pagination.next",
                "event.detail.todayEnds", "event.detail.section.title", "event.detail.back",
                "event.imageAlt", "event.detail.posterAlt")) {
            assertThat(translated.getProperty(key)).as("%s in messages%s", key, suffix)
                    .isNotEqualTo(korean.getProperty(key));
        }
        // 브랜드 눈썹 문구는 어느 언어에서도 영문 그대로 둔다.
        assertThat(translated.getProperty("event.kicker")).isEqualTo("TRAVEL DIARY EVENT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "_ko", "_en", "_ja", "_zh_CN", "_zh_TW"})
    void dayCountsAndTitlesTravelAsMessageParameters(String suffix) throws IOException {
        Properties bundle = bundle("/messages" + suffix + ".properties");

        // 언어마다 어순이 다르므로 문자열을 이어 붙이지 않고 파라미터로 넘긴다.
        for (String key : List.of("event.detail.totalDays", "event.detail.startsIn",
                "event.detail.endsIn", "event.imageAlt", "event.detail.posterAlt")) {
            assertThat(bundle.getProperty(key)).as("%s in messages%s", key, suffix)
                    .contains("{0}");
        }
        // 오늘 마감은 숫자가 없는 별도 문구다.
        assertThat(bundle.getProperty("event.detail.todayEnds")).doesNotContain("{0}");
    }

    @Test
    void theListScreenReadsItsFixedLabelsFromMessages() throws IOException {
        String list = resource(LIST);

        assertThat(list)
                .contains("#{event.kicker}")
                .contains("#{event.list.title}")
                .contains("#{event.list.description}")
                .contains("aria-label=#{event.list.tabs}")
                .contains("#{event.status.ongoing}")
                .contains("#{event.status.upcoming}")
                .contains("#{event.status.ended}")
                // 상태 배지는 판정 결과를 키로 삼는다. 화면에서 언어 분기를 만들지 않는다.
                .contains("#{'event.status.' + ${selectedStatus}}")
                .contains("#{event.imageAlt(${event.title})}")
                .contains("#{event.list.empty.ongoing}")
                .contains("#{event.list.empty.upcoming}")
                .contains("#{event.list.empty.ended}")
                .contains("aria-label=#{event.list.pagination}")
                .contains("aria-label=#{event.list.pagination.previous}")
                .contains("aria-label=#{event.list.pagination.next}");
    }

    @Test
    void theDetailScreenReadsItsFixedLabelsFromMessages() throws IOException {
        String detail = resource(DETAIL);

        assertThat(detail)
                .contains("#{event.kicker}")
                .contains("#{'event.status.' + ${status}}")
                .contains("#{event.detail.totalDays(${totalDays})}")
                .contains("#{event.detail.startsIn(${until})}")
                .contains("#{event.detail.endsIn(${left})}")
                .contains("#{event.detail.todayEnds}")
                .contains("#{event.detail.posterAlt(${event.title})}")
                .contains("#{event.imageAlt(${event.title})}")
                .contains("#{event.detail.section.title}")
                .contains("#{event.detail.back}");
    }

    @Test
    void thePublicScreensNoLongerBuildKoreanLabelsThemselves() throws IOException {
        for (String path : new String[]{LIST, DETAIL}) {
            String template = resource(path);
            // 남은 한글은 th:text 가 덮어쓰는 정적 예시 값과 주석뿐이라, 실제 표시 문구는 없다.
            assertThat(template).as(path)
                    .doesNotContain("aria-label=\"이벤트")
                    .doesNotContain("aria-label=\"이전 페이지\"")
                    .doesNotContain("aria-label=\"다음 페이지\"")
                    .doesNotContain("? '진행중' :")
                    .doesNotContain("|${event.title} 대표 이미지|")
                    .doesNotContain("|${event.title} 인포그래픽|")
                    .doesNotContain("|총 ${totalDays}일간|")
                    .doesNotContain("시작까지 D-${")
                    .doesNotContain("'오늘 마감' :")
                    .doesNotContain("종료까지 D-${");
        }
    }

    @Test
    void dynamicEventContentStillComesFromTheDatabaseLocalization() throws IOException {
        // 제목·상세 내용·포스터는 event_translations 에서 오는 값이라 messages 로 옮기지 않는다.
        assertThat(resource(LIST))
                .contains("th:text=\"${event.title}\"")
                .contains("th:text=\"${event.description}\"")
                .contains("event.posterImg");
        assertThat(resource(DETAIL))
                .contains("th:text=\"${event.title}\"")
                .contains("th:utext=\"${event.description}\"")
                .contains("th:src=\"@{${event.posterImg}}\"")
                .contains("th:src=\"@{${event.eventImg}}\"");
    }

    @Test
    void statusSortingAndPagingStructureIsUntouched() throws IOException {
        String list = resource(LIST);

        assertThat(list)
                .contains("@{/events(status='ongoing')}")
                .contains("@{/events(status='upcoming')}")
                .contains("@{/events(status='ended')}")
                .contains("page=${currentPage - 1}")
                .contains("page=${currentPage + 1}")
                .contains("size=${pageSize}");
        // 유형별 레이아웃 분기도 그대로다.
        assertThat(resource(DETAIL))
                .contains("event.eventType.name() == 'STANDARD'")
                .contains("posterLayout=${!isStandard and hasPoster}");
    }

    private Properties bundle(String path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
