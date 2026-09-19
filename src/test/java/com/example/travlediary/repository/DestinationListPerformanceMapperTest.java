package com.example.travlediary.repository;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationListPerformanceMapperTest {

    private static final String COUNTRY_CATEGORY_NAMESPACE =
            "com.example.travlediary.repository.category.CountryCategoryMapper";
    private static final String DESTINATION_NAMESPACE =
            "com.example.travlediary.repository.destination.DestinationMapper";

    @Test
    void descendantRegionIdsUseOneRecursiveQueryIncludingTheRoot() throws IOException {
        Configuration configuration = mapperConfiguration(
                "/mapper/CountryCategoryMapper.xml", "mapper/CountryCategoryMapper.xml");

        BoundSql boundSql = configuration.getMappedStatement(
                        COUNTRY_CATEGORY_NAMESPACE + ".findAllRegionIdsUnder")
                .getBoundSql(Map.of("rootRegionId", 7L));

        assertThat(normalize(boundSql.getSql()))
                .isEqualTo("WITH RECURSIVE region_tree AS ("
                        + "SELECT id FROM country_categories WHERE id = ? "
                        + "UNION ALL "
                        + "SELECT child.id FROM country_categories child "
                        + "JOIN region_tree parent ON child.parent_id = parent.id"
                        + ") SELECT id FROM region_tree");
    }

    @Test
    void defaultAndViewsSortDoNotCalculateBookmarkCounts() throws IOException {
        Configuration configuration = mapperConfiguration(
                "/mapper/DestinationMapper.xml", "mapper/DestinationMapper.xml");

        for (String sort : List.of("default", "views")) {
            String sql = destinationListSql(configuration, sort);
            String domesticSql = domesticDestinationListSql(configuration, sort);

            assertThat(sql)
                    .doesNotContain("FROM bookmarks")
                    .doesNotContain("bookmarkCount");
            assertThat(domesticSql)
                    .doesNotContain("FROM bookmarks")
                    .doesNotContain("bookmarkCount");
        }
    }

    @Test
    void bookmarksSortCalculatesAndOrdersByBookmarkCount() throws IOException {
        Configuration configuration = mapperConfiguration(
                "/mapper/DestinationMapper.xml", "mapper/DestinationMapper.xml");

        for (String sql : List.of(
                destinationListSql(configuration, "bookmarks"),
                domesticDestinationListSql(configuration, "bookmarks"))) {
            assertThat(sql)
                    .contains("SELECT COUNT(*) FROM bookmarks")
                    .contains("AS bookmarkCount")
                    .contains("ORDER BY bookmarkCount DESC");
        }
    }

    private String destinationListSql(Configuration configuration, String sort) {
        return normalize(configuration.getMappedStatement(
                        DESTINATION_NAMESPACE + ".findByRegionIdsPaged")
                .getBoundSql(Map.of(
                        "regionIds", List.of(7L, 38L, 235L),
                        "offset", 0,
                        "size", 12,
                        "sort", sort))
                .getSql());
    }

    private String domesticDestinationListSql(Configuration configuration, String sort) {
        return normalize(configuration.getMappedStatement(
                        DESTINATION_NAMESPACE + ".findDomesticPaged")
                .getBoundSql(Map.of(
                        "offset", 0,
                        "size", 12,
                        "sort", sort))
                .getSql());
    }

    private Configuration mapperConfiguration(String resource, String name) throws IOException {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, name,
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
