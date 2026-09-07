package com.example.travlediary.repository;

import com.example.travlediary.model.CategoryTranslation;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여행지 카테고리 이름 번역 Mapper 계약.
 *
 * <p>관리자 등록/수정 화면이 언어 한 줄씩 저장한다.
 * 이미 있던 목록 조회(findTranslationsByCategoryIds)와 카테고리 기본 조회는 그대로 둔다.
 */
class CategoryTranslationMapperContractTest {

    private static final String NAMESPACE =
            "com.example.travlediary.repository.category.CategoryMapper";

    @Test
    void singleCategoryTranslationsAreReadInDeterministicOrder() throws IOException {
        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + ".findTranslationsByCategoryId")
                .getBoundSql(Map.of("categoryId", 3L));

        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "SELECT id, category_id, language_code, name "
                        + "FROM category_translations WHERE category_id = ? "
                        + "ORDER BY language_code ASC, id ASC");
        assertThat(boundSql.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("categoryId");
    }

    @Test
    void insertWritesOneLanguageRowForTheGivenCategory() throws IOException {
        CategoryTranslation translation = new CategoryTranslation();
        translation.setCategoryId(3L);
        translation.setLanguageCode("en");
        translation.setName("Dessert");

        BoundSql boundSql = mapperConfiguration()
                .getMappedStatement(NAMESPACE + ".insertTranslation").getBoundSql(translation);

        assertThat(normalize(boundSql.getSql())).isEqualTo(
                "INSERT INTO category_translations (category_id, language_code, name) "
                        + "VALUES (?, ?, ?)");
        assertThat(boundSql.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("categoryId", "languageCode", "name");
    }

    @Test
    void updateAndDeleteTouchOnlyTheRequestedLanguageRow() throws IOException {
        Configuration configuration = mapperConfiguration();
        CategoryTranslation translation = new CategoryTranslation();
        translation.setCategoryId(3L);
        translation.setLanguageCode("ja");
        translation.setName("デザート");

        BoundSql update = configuration
                .getMappedStatement(NAMESPACE + ".updateTranslation").getBoundSql(translation);
        BoundSql delete = configuration
                .getMappedStatement(NAMESPACE + ".deleteTranslation")
                .getBoundSql(Map.of("categoryId", 3L, "languageCode", "zh-CN"));

        // 언어 코드는 조건일 뿐 갱신 대상이 아니다. 바꾸면 다른 언어 줄을 덮어쓰게 된다.
        assertThat(normalize(update.getSql())).isEqualTo(
                "UPDATE category_translations SET name = ? "
                        + "WHERE category_id = ? AND language_code = ?");
        assertThat(update.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("name", "categoryId", "languageCode");
        assertThat(normalize(delete.getSql())).isEqualTo(
                "DELETE FROM category_translations "
                        + "WHERE category_id = ? AND language_code = ?");
        assertThat(delete.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("categoryId", "languageCode");
    }

    @Test
    void addingTranslationStatementsDoesNotChangeTheCategoryStatements() throws IOException {
        Configuration configuration = mapperConfiguration();

        assertThat(normalize(configuration.getMappedStatement(NAMESPACE + ".findAll")
                .getBoundSql(null).getSql()))
                .isEqualTo("SELECT id, name FROM categories ORDER BY id");
        assertThat(normalize(configuration.getMappedStatement(NAMESPACE + ".findByDestinationType")
                .getBoundSql(Map.of("destinationType", "CAFE")).getSql()))
                .contains("FROM category_destination_types cdt")
                .doesNotContain("category_translations");
        // 목록·공개 화면이 쓰는 여러 건 조회는 그대로다
        assertThat(normalize(configuration
                .getMappedStatement(NAMESPACE + ".findTranslationsByCategoryIds")
                .getBoundSql(Map.of("categoryIds", java.util.List.of(3L, 4L))).getSql()))
                .isEqualTo("SELECT id, category_id, language_code, name "
                        + "FROM category_translations WHERE category_id IN (? , ?) "
                        + "ORDER BY category_id, language_code, id");
    }

    private Configuration mapperConfiguration() throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream input = getClass().getResourceAsStream("/mapper/CategoryMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, "mapper/CategoryMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ")
                .replaceAll("\\( ", "(")
                .replaceAll(" \\)", ")")
                .trim();
    }
}
