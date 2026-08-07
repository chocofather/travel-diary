package com.example.travlediary.repository;

import com.example.travlediary.dto.BoardListDto;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BoardMapperContractTest {

    @Test
    void listAggregatesBookmarksByTargetTypeAndTargetId() throws IOException {
        String mapper = resource("/mapper/BoardMapper.xml");
        String listQuery = between(mapper, "<select id=\"findBoardList\"", "</select>");

        assertThat(listQuery)
                .contains("b.target_type IN ('POST', 'COURSE')")
                .contains("GROUP BY b.target_type, b.target_id")
                .contains("bookmarkCounts.target_id = board.id")
                .contains("WHEN board.boardType = 'course' THEN 'COURSE'")
                .contains("ELSE 'POST'")
                .contains("COALESCE(bookmarkCounts.bookmarkCount, 0) AS bookmarkCount")
                .doesNotContain("'DESTINATION'");
    }

    @Test
    void bookmarkAggregationDoesNotChangeCountQuery() throws IOException {
        String mapper = resource("/mapper/BoardMapper.xml");
        String countQuery = between(mapper, "<select id=\"countBoard\"", "</select>");

        assertThat(countQuery)
                .contains("SELECT COUNT(*)")
                .doesNotContain("bookmarks")
                .doesNotContain("bookmarkCounts");
    }

    @Test
    void bookmarkSortReusesTheExistingAggregateWithStableTieBreakers() throws IOException {
        String mapper = resource("/mapper/BoardMapper.xml");
        String listQuery = between(mapper, "<select id=\"findBoardList\"", "</select>");

        assertThat(listQuery)
                .containsOnlyOnce("FROM bookmarks b")
                .contains("<when test=\"sort == 'bookmarks'\">")
                .contains("ORDER BY bookmarkCount DESC, board.createdAt DESC, board.boardType ASC, board.id DESC");
    }

    @Test
    void boardListDtoCarriesZeroSafeBookmarkCount() {
        BoardListDto dto = new BoardListDto();

        assertThat(dto.getBookmarkCount()).isZero();
        dto.setBookmarkCount(3);
        assertThat(dto.getBookmarkCount()).isEqualTo(3);
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
