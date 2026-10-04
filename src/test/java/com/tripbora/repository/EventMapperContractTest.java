package com.tripbora.repository;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventMapperContractTest {

    private static final String NAMESPACE = "com.tripbora.repository.event.EventMapper";

    @Test
    void updatePersistsEditableFieldsIncludingPreservedImagePaths() throws IOException {
        String mapper = resource("/mapper/EventMapper.xml");
        String update = between(mapper, "<update id=\"updateEvent\"", "</update>");

        assertThat(update)
                .contains("title = #{title}")
                .contains("event_type = #{eventType}")
                .contains("description = #{description}")
                .contains("event_img = #{eventImg}")
                .contains("poster_img = #{posterImg}")
                .contains("is_slide = #{slide}")
                .contains("start_date = #{startDate}")
                .contains("end_date = #{endDate}")
                .contains("WHERE id = #{id}")
                .doesNotContain("user_id =", "created_at =");
    }

    @Test
    void eventTypeIsMappedAndStoredOnCreate() throws IOException {
        String mapper = resource("/mapper/EventMapper.xml");
        String resultMap = between(mapper, "<resultMap id=\"EventMap\"", "</resultMap>");
        String insert = between(mapper, "<insert id=\"insert\"", "</insert>");

        assertThat(resultMap).contains("property=\"eventType\"", "column=\"event_type\"");
        assertThat(insert).contains("event_type", "#{eventType}");
    }

    @Test
    void slideQueryStillUsesActiveDateWindow() throws IOException {
        String mapper = resource("/mapper/EventMapper.xml");
        String query = between(mapper, "<select id=\"selectSlideEvents\"", "</select>");

        assertThat(query)
                .contains("is_slide = 1")
                .contains("start_date &lt;= NOW()")
                .contains("end_date &gt;= NOW()");
    }

    /**
     * 메인 프로모션 배너는 DB 시계(NOW())가 아니라 애플리케이션이 넘긴 오늘(KST, LocalDate)과 DATE 끼리 비교한다.
     * end_date >= today 라서 종료일 당일까지 남고, 종료일이 지난 이벤트만 빠진다.
     * 진행 중(종료 임박 순) → 진행 예정(시작 임박 순) → id 순이다.
     */
    @Test
    void homePromotionComparesDatesWithTheApplicationTodayAndKeepsTheLastDay() throws IOException {
        LocalDate today = LocalDate.of(2026, 10, 3);
        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + ".selectHomePromotionEvents")
                .getBoundSql(Map.of("today", today, "limit", 5));
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(sql)
                .contains("FROM events WHERE is_slide = 1 AND end_date >= ?")
                .contains("AND event_img IS NOT NULL AND event_img <> ''")
                .contains("ORDER BY CASE WHEN start_date <= ? THEN 0 ELSE 1 END ASC, "
                        + "CASE WHEN start_date <= ? THEN end_date END ASC, "
                        + "CASE WHEN start_date > ? THEN start_date END ASC, "
                        + "id ASC LIMIT ?")
                // 진행 예정도 남긴다. 시작일로 거르지 않는다.
                .doesNotContain("NOW()", "CURDATE()", "start_date <= ? AND");
        assertThat(boundSql.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("today", "today", "today", "today", "limit");
        assertThat(boundSql.getParameterObject())
                .isEqualTo(Map.of("today", today, "limit", 5));
    }

    private Configuration mapperConfiguration() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream input = getClass().getResourceAsStream("/mapper/EventMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, "mapper/EventMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
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
        assertThat(startIndex).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
