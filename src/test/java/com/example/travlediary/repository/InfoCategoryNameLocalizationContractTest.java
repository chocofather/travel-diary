package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공개 화면이 카테고리 이름을 언어별로 보여 줄 수 있게 되어 있는지 본다.
 *
 * <p>필터 값과 카테고리 원본은 그대로 두고 표시 이름만 바꾼다.
 */
class InfoCategoryNameLocalizationContractTest {

    @Test
    void filterPillsShowTheLocalizedNameButKeepTheCategoryIdAsTheFilterValue()
            throws IOException {
        String filter = resource("/templates/travel-info/fragments/category-filter.html");

        assertThat(filter)
                // 표시 이름은 언어별 맵에서 가져오고, 없으면 원문으로 떨어진다
                .contains("${categoryNames[category.id]}")
                .contains(": ${category.name}")
                // 필터 값·상태는 그대로 카테고리 번호를 쓴다
                .contains("data-filter-name=\"categoryId\"")
                .contains("data-filter-value=${category.id}")
                .contains("#lists.contains(categoryIds, category.id)");
    }

    @Test
    void publicDetailCarriesTheCategoryIdSoItsNameCanBeLocalized() throws IOException {
        String mapper = resource("/mapper/TravelInfoMapper.xml");
        String detail = between(mapper, "<select id=\"findPublicDetailById\"", "</select>");

        assertThat(detail)
                .contains("ti.category_id")
                .contains("ic.name AS category_name")
                // 작성자는 여전히 공개 화면에 내보내지 않는다
                .doesNotContain("ti.user_id");
        assertThat(between(mapper, "<resultMap id=\"PublicDetailResultMap\"", "</resultMap>"))
                .contains("<result property=\"categoryId\" column=\"category_id\"/>")
                .contains("<result property=\"categoryName\" column=\"category_name\"/>");
    }

    @Test
    void myPageTravelInfoBookmarksCarryTheCategoryIdToo() throws IOException {
        String mapper = resource("/mapper/MyPageBookmarkMapper.xml");
        String bookmarks = between(mapper,
                "<select id=\"findTravelInfoBookmarks\"", "</select>");

        assertThat(bookmarks)
                .contains("ic.id AS categoryId")
                .contains("ic.name AS categoryName");
    }

    @Test
    void publicListAlreadyCarriesTheCategoryIdForEveryCard() throws IOException {
        String mapper = resource("/mapper/TravelInfoMapper.xml");
        String list = between(mapper, "<select id=\"findPublicList\"", "</select>");

        // GENERAL / FESTIVAL 이 같은 목록 쿼리를 쓴다.
        assertThat(list)
                .contains("ti.category_id")
                .contains("ic.name AS category_name");
    }

    @Test
    void adminCategoryScreensStillShowTheKoreanBaseName() throws IOException {
        // 관리자 화면은 ko 고정이므로 번역 맵을 쓰지 않는다.
        assertThat(resource("/templates/admin/info-categories/list.html"))
                .contains("th:text=\"${category.name}\"")
                .doesNotContain("categoryNames");
        assertThat(resource("/templates/admin/travel-info/list.html"))
                .doesNotContain("categoryNames");
        assertThat(resource("/templates/admin/festivals/list.html"))
                .doesNotContain("categoryNames");
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
