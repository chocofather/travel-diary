package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalSearchMapperContractTest {

    @Test
    void unionSearchContainsOnlyTheSixPublicContentTypes() throws IOException {
        String mapper = resource("/mapper/GlobalSearchMapper.xml");
        String union = between(mapper, "<sql id=\"GlobalSearchUnion\">", "</sql>");

        assertThat(union)
                .contains("FROM destinations d")
                .contains("FROM user_posts p")
                .contains("FROM courses c")
                .contains("FROM travel_info ti")
                .contains("FROM events e")
                .contains("FROM notices n")
                .doesNotContain("FROM faqs")
                .doesNotContain("comments")
                .doesNotContain("inquiries");
    }

    @Test
    void eachDomainUsesItsSearchColumnsAndPublicConditions() throws IOException {
        String mapper = resource("/mapper/GlobalSearchMapper.xml");

        assertThat(mapper)
                .contains("dt.language_code = 'ko'")
                .contains("dt.name LIKE", "dt.short_description LIKE", "dt.description LIKE")
                .contains("cc.region_name LIKE", "pcc.region_name LIKE")
                .contains("FROM destination_images di")
                .contains("di.destination_id = d.id")
                .contains("di.is_main = 1")
                .contains("ORDER BY di.order_index ASC, di.id ASC")
                .contains("p.post_type IN ('QUESTION', 'TIP')")
                .contains("p.deleted = 0", "p.deleted_at IS NULL")
                .contains("p.title LIKE", "p.content LIKE")
                .contains("c.deleted = 0", "c.deleted_at IS NULL")
                .contains("c.title LIKE", "c.content LIKE")
                .contains("ic.is_visible = 1")
                .contains("ti.title LIKE", "ti.content LIKE")
                .contains("e.title LIKE", "e.description LIKE")
                .contains("COALESCE(NULLIF(e.event_img, ''), NULLIF(e.poster_img, '')) AS thumbnail_url")
                .contains("e.start_date", "e.end_date")
                .contains("n.title LIKE", "n.content LIKE");
    }

    @Test
    void typeFilterRelevanceAndPaginationUseBoundParameters() throws IOException {
        String mapper = resource("/mapper/GlobalSearchMapper.xml");
        String search = between(mapper, "<select id=\"search\"", "</select>");

        assertThat(mapper)
                .contains("#{type} IN ('all', 'destination')")
                .contains("#{type} IN ('all', 'community')")
                .contains("#{type} IN ('all', 'course')")
                .contains("#{type} IN ('all', 'travel-info')")
                .contains("#{type} IN ('all', 'event')")
                .contains("#{type} IN ('all', 'notice')")
                .doesNotContain("${");
        assertThat(search)
                .contains("thumbnail_url")
                .contains("ORDER BY relevance DESC, created_at DESC")
                .contains("LIMIT #{limit}")
                .contains("OFFSET #{offset}");
        assertThat(between(mapper, "<select id=\"count\"", "</select>"))
                .contains("SELECT COUNT(*)")
                .contains("GlobalSearchUnion");
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
