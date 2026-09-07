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

    /** FAQ 번역은 언어 한 줄이 단위다. 언어 코드는 조건일 뿐 갱신 대상이 아니다. */
    @Test
    void translationStatementsTouchOnlyTheRequestedLanguageRow() throws IOException {
        String xml = mapper();
        String select = between(xml, "<select id=\"findTranslationsByFaqId\"", "</select>");
        String insert = between(xml, "<insert id=\"insertTranslation\"", "</insert>");
        String update = between(xml, "<update id=\"updateTranslation\"", "</update>");
        String delete = between(xml, "<delete id=\"deleteTranslation\"", "</delete>");

        assertThat(select)
                .contains("SELECT id, faq_id, language_code, question, answer")
                .contains("FROM faq_translations")
                .contains("WHERE faq_id = #{faqId}")
                .contains("ORDER BY language_code ASC, id ASC");
        assertThat(insert)
                .contains("useGeneratedKeys=\"true\"")
                .contains("INSERT INTO faq_translations (faq_id, language_code, question, answer)")
                .contains("VALUES (#{faqId}, #{languageCode}, #{question}, #{answer})");
        assertThat(update)
                .contains("UPDATE faq_translations")
                .contains("question = #{question}", "answer = #{answer}")
                .contains("WHERE faq_id = #{faqId}", "AND language_code = #{languageCode}");
        assertThat(delete)
                .contains("DELETE FROM faq_translations")
                .contains("WHERE faq_id = #{faqId}", "AND language_code = #{languageCode}");
        // 언어와 무관한 값은 번역 테이블에 담지 않는다
        assertThat(insert + update)
                .doesNotContain("order_index", "is_visible", "category_id", "user_id");
    }

    /** 공개 목록은 질문마다 번역을 읽지 않고 한 번의 IN 조회로 모아 읽는다. */
    @Test
    void publicListTranslationsAreReadInOneQueryWithoutNPlusOne() throws IOException {
        String query = between(mapper(), "<select id=\"findTranslationsByFaqIds\"", "</select>");

        assertThat(query)
                .contains("SELECT id, faq_id, language_code, question, answer")
                .contains("FROM faq_translations")
                .contains("WHERE faq_id IN")
                .contains("<foreach collection=\"faqIds\"")
                // 빈 목록이 전체 조회로 넓어지지 않게 막는다
                .contains("WHERE 1 = 0")
                .contains("ORDER BY faq_id ASC, language_code ASC, id ASC");
    }

    /** 관리자 카테고리 관리. 목록은 사용 중인 FAQ 수까지 한 번에 읽고, 이름 중복은 사전에 센다. */
    @Test
    void categoryAdminStatementsCountUsageAndKeepTheExistingReads() throws IOException {
        String xml = mapper();
        String adminList = between(xml, "<select id=\"findAdminCategories\"", "</select>");
        String duplicate = between(xml, "<select id=\"countCategoriesByNameExcludingId\"",
                "</select>");
        String usage = between(xml, "<select id=\"countFaqsByCategoryId\"", "</select>");

        assertThat(adminList)
                .contains("FROM faq_categories fc")
                .contains("LEFT JOIN faqs f ON f.category_id = fc.id")
                .contains("COUNT(f.id) AS faq_count")
                .contains("GROUP BY fc.id, fc.category_name")
                .contains("ORDER BY fc.category_name ASC, fc.id ASC");
        assertThat(duplicate)
                .contains("FROM faq_categories")
                .contains("WHERE category_name = #{categoryName}")
                // 수정 시 자기 자신은 제외한다
                .contains("AND id &lt;&gt; #{excludeId}");
        assertThat(usage)
                .contains("SELECT COUNT(*)")
                .contains("FROM faqs")
                .contains("WHERE category_id = #{categoryId}");
        // FAQ 등록 화면과 공개 경로가 쓰던 조회는 그대로다
        assertThat(between(xml, "<select id=\"findCategories\"", "</select>"))
                .contains("SELECT id, category_name")
                .contains("FROM faq_categories")
                .contains("ORDER BY category_name ASC, id ASC");
        assertThat(between(xml, "<select id=\"findCategoryById\"", "</select>"))
                .contains("WHERE id = #{id}");
    }

    /** 카테고리 이름 번역은 언어 한 줄이 단위다. 언어 코드는 조건일 뿐 갱신 대상이 아니다. */
    @Test
    void categoryTranslationStatementsTouchOnlyTheRequestedLanguageRow() throws IOException {
        String xml = mapper();
        String select = between(xml, "<select id=\"findCategoryTranslationsByCategoryId\"",
                "</select>");
        String insert = between(xml, "<insert id=\"insertCategoryTranslation\"", "</insert>");
        String update = between(xml, "<update id=\"updateCategoryTranslation\"", "</update>");
        String delete = between(xml, "<delete id=\"deleteCategoryTranslation\"", "</delete>");

        assertThat(select)
                .contains("SELECT id, faq_category_id, language_code, category_name")
                .contains("FROM faq_category_translations")
                .contains("WHERE faq_category_id = #{faqCategoryId}")
                .contains("ORDER BY language_code ASC, id ASC");
        assertThat(insert)
                .contains("useGeneratedKeys=\"true\"")
                .contains("INSERT INTO faq_category_translations "
                        + "(faq_category_id, language_code, category_name)")
                .contains("VALUES (#{faqCategoryId}, #{languageCode}, #{categoryName})");
        assertThat(update)
                .contains("UPDATE faq_category_translations")
                .contains("SET category_name = #{categoryName}")
                .contains("WHERE faq_category_id = #{faqCategoryId}",
                        "AND language_code = #{languageCode}");
        assertThat(delete)
                .contains("DELETE FROM faq_category_translations")
                .contains("WHERE faq_category_id = #{faqCategoryId}",
                        "AND language_code = #{languageCode}");
    }

    /** 공개 목록은 카테고리마다 번역을 읽지 않고 한 번의 IN 조회로 모아 읽는다. */
    @Test
    void publicCategoryTranslationsAreReadInOneQueryWithoutNPlusOne() throws IOException {
        String query = between(mapper(),
                "<select id=\"findCategoryTranslationsByCategoryIds\"", "</select>");

        assertThat(query)
                .contains("SELECT id, faq_category_id, language_code, category_name")
                .contains("FROM faq_category_translations")
                .contains("WHERE faq_category_id IN")
                .contains("<foreach collection=\"faqCategoryIds\"")
                // 빈 목록이 전체 조회로 넓어지지 않게 막는다
                .contains("WHERE 1 = 0")
                .contains("ORDER BY faq_category_id ASC, language_code ASC, id ASC");
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
