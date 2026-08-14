package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EventMapperContractTest {

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
