package com.example.travlediary.repository;

import com.example.travlediary.model.Faq;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class FaqMapperContractTest {

    @Test
    void publicListUsesVisibleJoinAndConfiguredStableOrder() throws IOException {
        String query = between(mapper(), "<select id=\"findPublicList\"", "</select>");

        assertThat(query)
                .contains("FROM faqs f")
                .contains("JOIN faq_categories fc ON fc.id = f.category_id")
                .contains("WHERE f.is_visible = 1")
                .contains("ORDER BY f.order_index ASC, f.id ASC")
                .doesNotContain("LIMIT", "OFFSET");
    }

    @Test
    void adminListIncludesHiddenRowsAndUsesSameStableOrder() throws IOException {
        String query = between(mapper(), "<select id=\"findAdminList\"", "</select>");

        assertThat(query)
                .contains("JOIN faq_categories fc ON fc.id = f.category_id")
                .contains("ORDER BY f.order_index ASC, f.id ASC")
                .doesNotContain("WHERE f.is_visible = 1");
    }

    @Test
    void writesBindValuesAndUpdatePreservesOriginalAuthor() throws IOException {
        String xml = mapper();
        String insert = between(xml, "<insert id=\"insertFaq\"", "</insert>");
        String update = between(xml, "<update id=\"updateFaq\"", "</update>");

        assertThat(insert)
                .contains("useGeneratedKeys=\"true\"")
                .contains("question, answer, order_index, is_visible, category_id, user_id")
                .contains("#{question}", "#{answer}", "#{orderIndex}", "#{isVisible}",
                        "#{categoryId}", "#{userId}");
        assertThat(update)
                .contains("question = #{question}", "answer = #{answer}")
                .contains("order_index = #{orderIndex}", "is_visible = #{isVisible}")
                .contains("category_id = #{categoryId}")
                .doesNotContain("user_id =", "created_at =");
        assertThat(xml).doesNotContain("${");
    }

    @Test
    void modelUsesLongForBigintOrderIndex() throws NoSuchFieldException {
        assertThat(Faq.class.getDeclaredField("orderIndex").getType()).isEqualTo(Long.class);
    }

    private String mapper() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/mapper/FaqMapper.xml")) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
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
