package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalSearchMapperContractTest {

    /** 검색 대상은 공개 콘텐츠 여섯 갈래뿐이다. 갈래를 나눠 만들어도 그 범위는 그대로다. */
    @Test
    void unionSearchContainsOnlyTheSixPublicContentTypes() throws IOException {
        String mapper = resource("/mapper/GlobalSearchMapper.xml");

        assertThat(mapper)
                .contains("FROM destinations d")
                .contains("FROM user_posts p")
                .contains("FROM courses c")
                .contains("FROM travel_info ti")
                .contains("FROM events e")
                .contains("FROM notices n")
                .doesNotContain("FROM faqs")
                .doesNotContain("comments")
                .doesNotContain("inquiries");
        // 전체 검색은 여섯을 모두 붙이고, 갈래를 고르면 하나만 만든다.
        assertThat(between(mapper, "<sql id=\"searchCandidates\">", "</sql>"))
                .contains("destinationCandidate")
                .contains("communityCandidate")
                .contains("courseCandidate")
                .contains("travelInfoCandidate")
                .contains("eventCandidate")
                .contains("noticeCandidate");
    }

    @Test
    void eachDomainUsesItsSearchColumnsAndPublicConditions() throws IOException {
        String mapper = resource("/mapper/GlobalSearchMapper.xml");

        assertThat(mapper)
                .contains("dt.language_code = 'ko'")
                .contains("dt.name LIKE", "dt.short_description LIKE", "dt.description LIKE")
                .contains("cc.region_name LIKE", "pcc.region_name LIKE")
                .contains("FROM destination_images di")
                // 대표 이미지는 쪽을 자른 뒤 고른 줄(r)에 대해서만 찾는다. 고르는 규칙은 그대로다.
                .contains("di.destination_id = r.id")
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
                // 이벤트 대표 이미지는 예전처럼 event_img 를 먼저 쓰고 없으면 poster_img 로 내려간다.
                .contains("COALESCE(NULLIF(e.event_img, ''), NULLIF(e.poster_img, ''))")
                .contains("AS thumbnail_url")
                .contains("e.start_date", "e.end_date")
                .contains("n.title LIKE", "n.content LIKE");
    }

    /**
     * 검색어와 쪽 번호는 언제나 바인딩 파라미터로 들어간다. 문자열로 이어 붙이지 않는다.
     *
     * <p>갈래를 고르는 일은 이제 SQL 안의 {@code #{type} IN (...)} 이 아니라 MyBatis 가 한다.
     * 고른 값은 SQL 문자열로 나가지 않고 어떤 조각을 쓸지만 정한다.
     */
    @Test
    void typeFilterRelevanceAndPaginationUseBoundParameters() throws IOException {
        String mapper = resource("/mapper/GlobalSearchMapper.xml");
        String search = between(mapper, "<select id=\"search\"", "</select>");

        assertThat(mapper).doesNotContain("${");
        assertThat(mapper)
                .contains("test=\"type == 'destination'\"")
                .contains("test=\"type == 'community'\"")
                .contains("test=\"type == 'course'\"")
                .contains("test=\"type == 'travel-info'\"")
                .contains("test=\"type == 'event'\"")
                .contains("test=\"type == 'notice'\"");
        assertThat(search)
                .contains("searchThumbnail")
                .contains("ORDER BY relevance DESC, created_at DESC")
                .contains("LIMIT #{limit}")
                .contains("OFFSET #{offset}");
        assertThat(between(mapper, "<sql id=\"searchThumbnail\">", "</sql>"))
                .contains("AS thumbnail_url");
        // 개수 세기는 목록과 같은 조건 조각을 쓴다.
        assertThat(between(mapper, "<select id=\"count\"", "</select>"))
                .contains("SELECT COUNT(*)")
                .contains("destinationWhere")
                .contains("communityWhere")
                .contains("courseWhere")
                .contains("travelInfoWhere")
                .contains("eventWhere")
                .contains("noticeWhere");
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
