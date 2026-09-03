package com.example.travlediary.repository;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상세 화면 편의시설 조회는 언어를 SQL 에서 고르지 않는다.
 *
 * <p>번역을 한 번에 모두 읽고, 화면에 쓸 이름은 서비스가 고른다.
 * 관리자 화면이 쓰는 조회는 예전처럼 언어를 지정해 읽는다.
 */
class AmenityLocaleMapperContractTest {

    private static final String[] DETAIL_SELECTS = {
            "findAttractionAmenityTranslationsByDestinationId",
            "findAccommodationAmenityTranslationsByDestinationId",
            "findRestaurantAmenityTranslationsByDestinationId",
            "findActivityAmenityTranslationsByDestinationId",
            "findShopAmenityTranslationsByDestinationId"
    };

    @Test
    void detailSelectsReadEveryTranslationInOneDeterministicQuery() throws IOException {
        String mapper = mapperXml();

        for (String select : DETAIL_SELECTS) {
            String query = between(mapper, "<select id=\"" + select + "\"", "</select>");
            assertThat(query).as(select)
                    .contains("<include refid=\"AmenityTranslationColumns\"/>")
                    .contains("LEFT JOIN amenity_translations t ON t.amenity_id = a.id")
                    .contains("<include refid=\"AmenityTranslationOrder\"/>")
                    // 언어를 SQL 에서 고르지 않는다
                    .doesNotContain("t.language_code =")
                    .doesNotContain("#{lang}")
                    .doesNotContain("${");
        }

        // 편의시설 번호로 묶고, 남은 언어를 고를 때 늘 같은 차례가 되도록 정렬한다
        assertThat(between(mapper, "<sql id=\"AmenityTranslationOrder\">", "</sql>"))
                .contains("ORDER BY a.id ASC, t.language_code ASC, t.id ASC");
        // 번역이 없는 편의시설도 code 로 보이도록 code 를 함께 읽는다
        assertThat(between(mapper, "<sql id=\"AmenityTranslationColumns\">", "</sql>"))
                .contains("a.id AS amenity_id")
                .contains("a.code AS code")
                .contains("t.language_code AS language_code");
    }

    @Test
    void adminSelectsStillPickTheirLanguageInSql() throws IOException {
        String mapper = mapperXml();

        assertThat(between(mapper, "<select id=\"findTranslationsByLang\"", "</select>"))
                .contains("language_code = #{languageCode}");
        assertThat(between(mapper, "<select id=\"findTranslationsByTypeAndLang\"", "</select>"))
                .contains("t.language_code = #{languageCode}");
        assertThat(between(mapper, "<select id=\"findAdminAmenityRows\"", "</select>"))
                .contains("t.language_code = 'ko'");
    }

    @Test
    void detailScreenStillUsesTheAmenityNameForItsIconTextAndTooltip() throws IOException {
        String detail = resource("/templates/destination/detail.html");
        var icons = Jsoup.parse(detail).select("img.amenity-icon");

        // 아이콘 대체 텍스트와 말풍선은 서버가 고른 이름을 그대로 쓴다 (5개 유형 모두)
        assertThat(count(detail, "data-tooltip=${a.name}")).isEqualTo(5);
        assertThat(icons).hasSize(5).allSatisfy(icon -> {
            assertThat(icon.attr("th:alt")).isEqualTo("${a.name}");
            assertThat(icon.attr("th:src")).contains("a.iconUrl").contains("a.code");
        });
    }

    private int count(String source, String token) {
        return source.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }

    private String between(String source, String start, String end) {
        int from = source.indexOf(start);
        assertThat(from).as("start %s", start).isNotNegative();
        int to = source.indexOf(end, from);
        assertThat(to).as("end %s", end).isNotNegative();
        return source.substring(from, to);
    }

    private String mapperXml() throws IOException {
        return resource("/mapper/AmenityMapper.xml");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
