package com.example.travlediary.repository;

import com.example.travlediary.model.DiaryNotebookType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 다이어리 한 권이 어떤 공책인지(일반 / 스프링)를 담는 칸.
 *
 * <p>표지(cover_style)와는 다른 축이다. 표지는 목록의 책 겉모습이고 이쪽은 펼쳤을 때의 모양이라,
 * 한 칸이 다른 칸을 덮어쓰지 않도록 저장 경로 네 곳에 모두 실려 있어야 한다.
 */
class DiaryNotebookTypeMapperContractTest {

    @Test
    void theNotebookTypeIsReadBackIntoTheDiary() throws IOException {
        String mapper = diaryMapper();

        assertThat(between(mapper, "<resultMap id=\"DiaryMap\"", "</resultMap>"))
                .contains("property=\"notebookType\"")
                .contains("column=\"notebook_type\"");
        // 조회 컬럼 목록에도 있어야 상세/편집 화면까지 값이 실려 온다
        assertThat(between(mapper, "<sql id=\"DiaryColumns\">", "</sql>"))
                .contains("notebook_type");
    }

    @Test
    void theNotebookTypeIsSavedWithTheDiaryAndChangedWithIt() throws IOException {
        String mapper = diaryMapper();

        String insert = between(mapper, "<insert id=\"insert\"", "</insert>");
        assertThat(insert)
                .contains("notebook_type")
                .contains("#{notebookType}");

        String update = between(mapper, "<update id=\"update\"", "</update>");
        assertThat(update).contains("notebook_type = #{notebookType}");
        // 표지는 그대로 따로 저장된다. (두 축이 서로를 덮지 않는다)
        assertThat(update).contains("cover_style = #{coverStyle}");
    }

    @Test
    void theTypeIsKeptOnTheBookNotOnEachPage() throws IOException {
        // 한 권에 하나다. 새 장을 더해도 같은 공책이어야 하므로 페이지 저장소는 이 칸을 모른다.
        assertThat(resource("/mapper/DiaryPageMapper.xml")).doesNotContain("notebook_type");
    }

    @Test
    void theListDoesNotNeedTheTypeYet() throws IOException {
        // 목록은 책 겉모습(표지)만 그린다. 필요해질 때 따로 싣는다.
        assertThat(between(diaryMapper(), "<resultMap id=\"DiaryListItemMap\"", "</resultMap>"))
                .doesNotContain("notebook_type");
    }

    @Test
    void theCodeAllowsExactlyTheTwoShapesTheColumnStores() {
        assertThat(DiaryNotebookType.values()).hasSize(2);
        assertThat(DiaryNotebookType.CLASSIC.getCode()).isEqualTo("CLASSIC");
        assertThat(DiaryNotebookType.SPIRAL.getCode()).isEqualTo("SPIRAL");
        // DB 기본값과 같은 값이 코드의 기본값이다
        assertThat(DiaryNotebookType.CLASSIC.getCssClass()).isEqualTo("diary-book-classic");
        assertThat(DiaryNotebookType.SPIRAL.getCssClass()).isEqualTo("diary-book-spiral");
    }

    private String diaryMapper() throws IOException {
        return resource("/mapper/DiaryMapper.xml");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("end %s", end).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
