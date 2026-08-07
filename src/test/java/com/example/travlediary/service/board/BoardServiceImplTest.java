package com.example.travlediary.service.board;

import com.example.travlediary.repository.board.BoardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BoardServiceImplTest {

    @Mock
    private BoardMapper boardMapper;

    private BoardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BoardServiceImpl(boardMapper);
    }

    @Test
    void listNormalizesFiltersSortAndPagination() {
        service.getBoardList("POST", "tip", "unsupported", 0, 200);

        verify(boardMapper).findBoardList("post", "TIP", "latest", 0L, 100);
    }

    @Test
    void listKeepsSupportedSortAndCalculatesOffset() {
        service.getBoardList("course", null, "comments", 3, 10);

        verify(boardMapper).findBoardList("course", null, "comments", 20L, 10);
    }

    @Test
    void listKeepsBookmarkSort() {
        service.getBoardList(null, null, "BOOKMARKS", 1, 10);

        verify(boardMapper).findBoardList(null, null, "bookmarks", 0L, 10);
    }

    @Test
    void countUsesTheSameNormalizedFilters() {
        service.getBoardCount("invalid", "question");

        verify(boardMapper).countBoard(null, "QUESTION");
    }

    @Test
    void profileQuestionFilterUsesDedicatedAuthorQueryAndPagination() {
        service.getBoardListByUserId(7L, "QUESTION", 2, 10);

        verify(boardMapper).findBoardListByUserId(7L, "post", "QUESTION", 10L, 10);
    }

    @Test
    void profileFiltersMapTipCourseAndInvalidTypeSafely() {
        service.getBoardCountByUserId(7L, "tip");
        service.getBoardCountByUserId(7L, "course");
        service.getBoardCountByUserId(7L, "unsupported");

        verify(boardMapper).countBoardByUserId(7L, "post", "TIP");
        verify(boardMapper).countBoardByUserId(7L, "course", null);
        verify(boardMapper).countBoardByUserId(7L, null, null);
    }
}
