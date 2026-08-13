package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HomePopularCourseMapperContractTest {

    @Test
    void popularCoursesAreFilteredAndRankedBeforeTheBoundLimit() throws IOException {
        String mapper = resource("/mapper/CourseMapper.xml");
        String query = between(mapper, "<select id=\"findPopularCourses\"", "</select>");

        assertThat(query)
                .contains("FROM courses c")
                .contains("JOIN users u ON u.id = c.user_id")
                .contains("JOIN course_destinations cd ON cd.course_id = c.id")
                .doesNotContain("LEFT JOIN course_destinations cd")
                .contains("c.deleted = 0")
                .contains("c.deleted_at IS NULL")
                .contains("GROUP BY c.id")
                .contains("ORDER BY c.views DESC, c.created_at DESC, c.id DESC")
                .contains("LIMIT #{limit}");
    }

    @Test
    void previewStopsAreLoadedInOneBatchAndVisitOrder() throws IOException {
        String mapper = resource("/mapper/CourseMapper.xml");
        String query = between(mapper, "<select id=\"findPopularCourseStops\"", "</select>");

        assertThat(query)
                .contains("FROM course_destinations cd")
                .contains("dt.language_code = 'ko'")
                .contains("WHERE cd.course_id IN")
                .contains("<foreach collection=\"courseIds\"")
                .contains("ORDER BY cd.course_id ASC, cd.visit_order ASC, cd.id ASC")
                .doesNotContain("LIMIT 3");
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
