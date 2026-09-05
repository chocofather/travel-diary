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
 * 공개 여행정보·축제 목록 화면의 고정 문구가 messages 로 옮겨졌는지 본다.
 *
 * <p>카테고리 이름처럼 DB 에서 언어별로 오는 값은 messages 대상이 아니다.
 */
class TravelInfoListMessagesContractTest {

    private static final String LIST = "/templates/travel-info/list.html";
    private static final String CATEGORY_FILTER =
            "/templates/travel-info/fragments/category-filter.html";
    private static final String RESULTS = "/templates/travel-info/fragments/list-results.html";

    /** 목록 화면이 쓰는 키. 여섯 번들에 모두 있어야 한다. */
    private static final List<String> KEYS = List.of(
            "travelInfo.list.kicker",
            "travelInfo.list.title.general", "travelInfo.list.title.festival",
            "travelInfo.list.description.general", "travelInfo.list.description.festival",
            "travelInfo.list.filters",
            "travelInfo.list.search.label", "travelInfo.list.search.placeholder",
            "travelInfo.list.search.inputLabel", "travelInfo.list.search.clear",
            "travelInfo.list.search.submit",
            "travelInfo.list.filter.scope.general", "travelInfo.list.filter.scope.festival",
            "travelInfo.list.filter.category.general", "travelInfo.list.filter.category.festival",
            "travelInfo.list.filter.reset",
            "travelInfo.filter.all",
            "travelInfo.scope.domestic", "travelInfo.scope.international",
            "travelInfo.contentType.general",
            "travelInfo.list.total",
            "travelInfo.list.sort.label", "travelInfo.list.sort.latest", "travelInfo.list.sort.views",
            "travelInfo.list.card.thumbnailAlt", "travelInfo.list.card.noImage",
            "travelInfo.list.views",
            "travelInfo.list.empty.keyword", "travelInfo.list.empty.keyword.hint",
            "travelInfo.list.empty.filter", "travelInfo.list.empty.filter.hint",
            "travelInfo.list.pagination",
            "travelInfo.list.pagination.previous", "travelInfo.list.pagination.next",
            "travelInfo.list.loadFailed",
            "travelInfo.bookmark.save", "travelInfo.bookmark.remove",
            "travelInfo.bookmark.label.save", "travelInfo.bookmark.label.saved",
            "travelInfo.bookmark.failed");

    @ParameterizedTest
    @ValueSource(strings = {"", "_ko", "_en", "_ja", "_zh_CN", "_zh_TW"})
    void everyBundleCarriesEveryListKey(String suffix) throws IOException {
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

        // 화면에서 바로 눈에 띄는 라벨들이 실제로 번역돼 있어야 한다.
        for (String key : List.of("travelInfo.list.title.general", "travelInfo.list.title.festival",
                "travelInfo.filter.all", "travelInfo.scope.domestic", "travelInfo.scope.international",
                "travelInfo.list.sort.latest", "travelInfo.list.sort.views",
                "travelInfo.list.pagination.previous", "travelInfo.list.pagination.next",
                "travelInfo.bookmark.label.save", "travelInfo.bookmark.label.saved")) {
            assertThat(translated.getProperty(key)).as("%s in messages%s", key, suffix)
                    .isNotEqualTo(korean.getProperty(key));
        }
    }

    @Test
    void countAndKeywordUseMessageParametersInsteadOfStringConcatenation() throws IOException {
        for (String suffix : new String[]{"", "_ko", "_en", "_ja", "_zh_CN", "_zh_TW"}) {
            Properties bundle = bundle("/messages" + suffix + ".properties");
            assertThat(bundle.getProperty("travelInfo.list.total")).contains("{0}");
            assertThat(bundle.getProperty("travelInfo.list.views")).contains("{0}");
            assertThat(bundle.getProperty("travelInfo.list.empty.keyword")).contains("{0}");
            assertThat(bundle.getProperty("travelInfo.list.card.thumbnailAlt")).contains("{0}");
        }
        assertThat(resource(RESULTS))
                .contains("#{travelInfo.list.total(${totalCount})}")
                .contains("#{travelInfo.list.views(${info.views})}")
                .contains("#{travelInfo.list.empty.keyword(${keyword})}")
                .contains("#{travelInfo.list.card.thumbnailAlt(${info.title})}");
    }

    @Test
    void theListScreenNoLongerCarriesItsOwnKoreanLabels() throws IOException {
        for (String path : new String[]{LIST, CATEGORY_FILTER, RESULTS}) {
            String template = resource(path);
            // 남은 한글은 th:text 가 덮어쓰는 정적 예시 값과 주석뿐이라, 실제 표시 문구는 없다.
            assertThat(template).as(path)
                    .doesNotContain("aria-label=\"여행정보")
                    .doesNotContain("aria-label=\"검색어")
                    .doesNotContain("placeholder=\"파리")
                    .doesNotContain("'축제·행사' : '여행정보'")
                    .doesNotContain("? '축제·행사 분류' : '주제'")
                    .doesNotContain("'저장됨' : '저장'")
                    .doesNotContain("'국내' : '해외'");
        }
    }

    @Test
    void generalAndFestivalKeepTheirOwnLabelsAndFilterValues() throws IOException {
        String list = resource(LIST);

        // GENERAL 은 국내/해외, FESTIVAL 은 전체/국내/해외 — 구조는 그대로다.
        assertThat(list)
                .contains("#{travelInfo.list.filter.scope.general}")
                .contains("#{travelInfo.list.filter.scope.festival}")
                .contains("#{travelInfo.filter.all}")
                .contains("#{travelInfo.list.title.festival}")
                .contains("#{travelInfo.list.description.festival}");
        // 필터 값·파라미터는 messages 로 옮기지 않는다.
        assertThat(list)
                .contains("data-filter-value=\"DOMESTIC\"")
                .contains("data-filter-value=\"INTERNATIONAL\"")
                .contains("data-filter-content-type=\"GENERAL\"")
                .contains("data-filter-content-type=\"FESTIVAL\"")
                .contains("contentType='FESTIVAL'")
                .contains("name=\"keyword\"");
    }

    @Test
    void categoryNamesStillComeFromTheDatabaseLocalization() throws IOException {
        String filter = resource(CATEGORY_FILTER);

        // 카테고리 이름은 info_category_translations 결과를 그대로 쓴다.
        assertThat(filter)
                .contains("${categoryNames[category.id]}")
                .contains(": ${category.name}")
                .contains("data-filter-name=\"categoryId\"")
                .contains("data-filter-value=${category.id}");
        // 카테고리 이름을 messages 로 옮기지 않았다.
        assertThat(bundle("/messages_ko.properties").stringPropertyNames())
                .noneMatch(key -> key.startsWith("travelInfo.category."));
        assertThat(resource(RESULTS)).contains("${info.categoryName}");
    }

    @Test
    void scriptsReadTheirWordingFromTheServerInsteadOfHardCodingLanguages()
            throws IOException {
        assertThat(resource(LIST))
                .contains("data-load-failed-message=#{travelInfo.list.loadFailed}");
        assertThat(resource("/static/js/travel-info-list.js"))
                .contains("messageElement?.dataset.loadFailedMessage")
                .doesNotContain("'목록을 불러오지 못했습니다. 페이지를 다시 불러옵니다.'");
        assertThat(resource("/static/js/travel-info-bookmark.js"))
                .contains("text(button, 'labelSaved'")
                .contains("text(button, 'ariaRemove'")
                .contains("text(button, 'failedMessage'");
    }

    @Test
    void detailScreensNowShareTheSameMessageKeys() throws IOException {
        // 상세도 같은 번들을 쓴다. 의미가 같은 키는 목록에서 만든 것을 그대로 쓴다.
        assertThat(resource("/templates/travel-info/detail.html"))
                .contains("#{travelInfo.list.title.general}")
                .contains("#{travelInfo.scope.domestic}")
                .contains("#{travelInfo.bookmark.label.saved}");
        assertThat(resource("/templates/festivals/detail.html"))
                .contains("#{travelInfo.list.views(${festival.travelInfo.views})}")
                .contains("#{travelInfo.bookmark.save}");
    }

    private Properties bundle(String path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
