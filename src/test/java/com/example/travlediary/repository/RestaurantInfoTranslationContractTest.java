package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 식당 번역 테이블과 조회 규약.
 *
 * <p>자유 텍스트만 담고, 여행지 하나의 번역을 한 번에 읽는다.
 * 관리자용 원본 CRUD 문장은 그대로 둔다.
 */
class RestaurantInfoTranslationContractTest {

    @Test
    void translationSelectReadsEveryFreeTextFieldInOneDeterministicQuery() throws IOException {
        String mapper = resource("/mapper/RestaurantInfoMapper.xml");
        String select = between(mapper,
                "<select id=\"findTranslationsByDestinationId\"", "</select>");

        assertThat(select)
                .contains("FROM restaurant_info_translations")
                .contains("WHERE destination_id = #{destinationId}")
                .contains("main_menu")
                .contains("price_range")
                .contains("opening_hours")
                .contains("break_time")
                .contains("closed_days")
                .contains("etc")
                // 남은 언어를 고를 때 늘 같은 차례가 되도록 정렬한다
                .contains("ORDER BY language_code ASC, id ASC")
                // 언어는 SQL 에서 고르지 않는다
                .doesNotContain("language_code =")
                .doesNotContain("${");
        // 언어와 무관한 값은 번역 테이블에서 읽지 않는다
        assertThat(select)
                .doesNotContain("contact_number")
                .doesNotContain("homepage_url")
                .doesNotContain("seat_count")
                .doesNotContain("parking_available")
                .doesNotContain("reservation");
    }

    @Test
    void baseRestaurantCrudStatementsAreUnchanged() throws IOException {
        String mapper = resource("/mapper/RestaurantInfoMapper.xml");

        assertThat(between(mapper, "<insert id=\"insert\"", "</insert>"))
                .contains("INSERT INTO restaurant_info")
                .contains("#{mainMenu}")
                .contains("#{contactNumber}")
                .doesNotContain("restaurant_info_translations");
        assertThat(between(mapper, "<select id=\"findByDestinationId\"", "</select>"))
                .contains("SELECT * FROM restaurant_info WHERE destination_id = #{destinationId}");
        assertThat(between(mapper, "<update id=\"update\"", "</update>"))
                .contains("UPDATE restaurant_info")
                .contains("etc = #{etc}")
                .doesNotContain("restaurant_info_translations");
    }

    @Test
    void theMigrationSqlMatchesTheAgreedTranslationTableShape() throws IOException {
        String sql = Files.readString(
                Path.of("docs/db/restaurant_info_translations.sql"), StandardCharsets.UTF_8);

        // 이름과 타입은 실제 DB(SHOW CREATE TABLE) 와 같아야 한다
        assertThat(sql)
                .contains("CREATE TABLE `restaurant_info_translations`")
                .contains("`destination_id` bigint NOT NULL")
                .contains("`language_code` varchar(10)")
                .contains("UNIQUE KEY `uk_restaurant_info_translation` "
                        + "(`destination_id`,`language_code`)")
                .contains("KEY `idx_restaurant_info_translation_lang` "
                        + "(`language_code`,`destination_id`)")
                .contains("CONSTRAINT `fk_restaurant_info_translation`")
                .contains("REFERENCES `restaurant_info` (`destination_id`)")
                .contains("ON DELETE CASCADE")
                .contains("DEFAULT CHARSET=utf8mb4")
                .contains("ko / en / ja / zh-CN / zh-TW")
                // 자유 텍스트 6개만 담는다
                .contains("`main_menu`")
                .contains("`price_range`")
                .contains("`opening_hours`")
                .contains("`break_time`")
                .contains("`closed_days`")
                .contains("`etc`")
                .doesNotContain("`contact_number`")
                .doesNotContain("`homepage_url`")
                .doesNotContain("`seat_count`");
        // 번역 데이터는 이번 단계에서 만들지 않는다 (예시는 주석으로만)
        assertThat(sql.lines()
                .map(String::strip)
                .filter(line -> line.toUpperCase().startsWith("INSERT INTO")))
                .isEmpty();
    }

    private String between(String source, String start, String end) {
        int from = source.indexOf(start);
        assertThat(from).as("start %s", start).isNotNegative();
        int to = source.indexOf(end, from);
        assertThat(to).as("end %s", end).isNotNegative();
        return source.substring(from, to);
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
