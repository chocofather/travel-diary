package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TravelInfoMapperContractTest {

    @Test
    void adminListJoinsCategoryAndUsesAllFiltersWithStableOrder() throws IOException {
        String mapper = mapper();
        String query = between(mapper, "<select id=\"findAdminList\"", "</select>");

        assertThat(query)
                .contains("FROM travel_info ti")
                .contains("JOIN info_categories ic ON ic.id = ti.category_id")
                .contains("<if test=\"scope != null\">")
                .contains("<if test=\"contentType != null\">")
                .contains("<if test=\"categoryId != null\">")
                .contains("ORDER BY ti.created_at DESC, ti.id DESC")
                .doesNotContain("UPDATE travel_info", "views = views + 1");
    }

    @Test
    void travelInfoInsertUsesGeneratedKeyAndServerManagedViews() throws IOException {
        String insert = between(mapper(), "<insert id=\"insertTravelInfo\"", "</insert>");

        assertThat(insert)
                .contains("useGeneratedKeys=\"true\"")
                .contains("keyProperty=\"id\"")
                .contains("INSERT INTO travel_info")
                .contains("title, content, scope, content_type, category_id, views, user_id")
                .contains("#{categoryId}, 0, #{userId}");
    }

    @Test
    void updatePreservesViewsAndUserAndPeriodQueriesUseInfoPeriods() throws IOException {
        String mapper = mapper();
        String update = between(mapper, "<update id=\"updateTravelInfo\"", "</update>");
        String periods = between(mapper, "<select id=\"findPeriodsByInfoId\"", "</select>");
        String periodInsert = between(mapper, "<insert id=\"insertPeriod\"", "</insert>");
        String periodDelete = between(mapper, "<delete id=\"deletePeriodsByInfoId\"", "</delete>");

        assertThat(update)
                .contains("UPDATE travel_info")
                .doesNotContain("views =", "user_id =");
        assertThat(periods)
                .contains("FROM info_periods")
                .contains("ORDER BY start_date ASC, end_date ASC, id ASC");
        assertThat(periodInsert).contains("INSERT INTO info_periods (start_date, end_date, info_id)");
        assertThat(periodDelete).contains("DELETE FROM info_periods", "WHERE info_id = #{infoId}");
    }

    @Test
    void mainThumbnailQueriesUseOnlyMainInfoImagesAndStableOrdering() throws IOException {
        String mapper = mapper();
        String findOne = between(mapper, "<select id=\"findMainImageByInfoId\"", "</select>");
        String findAllUrls = between(mapper, "<select id=\"findMainImageUrlsByInfoId\"", "</select>");
        String insert = between(mapper, "<insert id=\"insertInfoImage\"", "</insert>");
        String delete = between(mapper, "<delete id=\"deleteMainImagesByInfoId\"", "</delete>");

        assertThat(findOne)
                .contains("FROM info_images")
                .contains("info_id = #{infoId}")
                .contains("is_main = 1")
                .contains("ORDER BY order_index ASC, id ASC")
                .contains("LIMIT 1");
        assertThat(findAllUrls)
                .contains("SELECT image_url")
                .contains("FROM info_images")
                .contains("is_main = 1")
                .contains("ORDER BY order_index ASC, id ASC")
                .doesNotContain("LIMIT 1");
        assertThat(insert)
                .contains("INSERT INTO info_images (image_url, is_main, order_index, info_id, created_at)")
                .contains("#{imageUrl}, #{isMain}, #{orderIndex}, #{infoId}, NOW()")
                .contains("useGeneratedKeys=\"true\"");
        assertThat(delete)
                .contains("DELETE FROM info_images")
                .contains("info_id = #{infoId}")
                .contains("is_main = 1");
    }

    @Test
    void mapperDoesNotReferenceExcludedDomainsOrViewIncrement() throws IOException {
        assertThat(mapper())
                .doesNotContain("events", "destinations", "incrementViews", "increment_views");
    }

    @Test
    void schemaReferenceCascadesInfoImagesWhenTravelInfoIsDeleted() throws IOException {
        String schema = Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
        String infoImages = between(schema, "CREATE TABLE `info_images`", ") ENGINE=InnoDB");

        assertThat(infoImages)
                .contains("FOREIGN KEY (`info_id`) REFERENCES `travel_info` (`id`) ON DELETE CASCADE");
    }

    private String mapper() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/mapper/TravelInfoMapper.xml")) {
            assertThat(input).isNotNull();
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
