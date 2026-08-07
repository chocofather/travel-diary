package com.example.travlediary.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    @Test
    void lastPageUsesThreadTotalInsteadOfReturnedRowCount() {
        PageResult<Integer> exactlyFiveThreadsWithReplies =
                new PageResult<>(List.of(1, 2, 3, 4, 5, 6, 7, 8), 5, 0, 5, 8);
        PageResult<Integer> sixThreads =
                new PageResult<>(List.of(1, 2, 3, 4, 5, 6, 7, 8), 6, 0, 5, 8);

        assertThat(exactlyFiveThreadsWithReplies.isLast()).isTrue();
        assertThat(sixThreads.isLast()).isFalse();
        assertThat(sixThreads.getTotalCommentCount()).isEqualTo(8);
    }

    @Test
    void outOfRangePageCanKeepTheOriginalThreadTotal() {
        PageResult<Integer> result = new PageResult<>(List.of(), 6, 3, 5, 12);

        assertThat(result.getTotalElements()).isEqualTo(6);
        assertThat(result.getTotalCommentCount()).isEqualTo(12);
        assertThat(result.isLast()).isTrue();
    }
}
