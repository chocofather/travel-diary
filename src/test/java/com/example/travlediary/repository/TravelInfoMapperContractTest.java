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
    void publicListAndCountShareVisibleCategoryAndOptionalFilters() throws IOException {
        String mapper = mapper();
        String filters = between(mapper, "<sql id=\"PublicListFilters\"", "</sql>");
        String list = between(mapper, "<select id=\"findPublicList\"", "</select>");
        String count = between(mapper, "<select id=\"countPublicList\"", "</select>");

        assertThat(filters)
                .contains("ic.is_visible = 1")
                .contains("<if test=\"scope != null\">", "ti.scope = #{scope}")
                .contains("<if test=\"contentType != null\">", "ti.content_type = #{contentType}")
                .contains("<if test=\"categoryIds != null and !categoryIds.isEmpty()\">")
                .contains("AND ti.category_id IN")
                .contains("<foreach collection=\"categoryIds\"")
                .contains("item=\"categoryId\"")
                .contains("open=\"(\"", "separator=\",\"", "close=\")\"")
                .contains("#{categoryId}")
                .contains("<if test=\"keywordPattern != null and !keywordPattern.isEmpty()\">")
                .contains("AND (")
                .contains("ti.title LIKE CONCAT('%', #{keywordPattern}, '%') ESCAPE '!'")
                .contains("<if test=\"koreanPattern != null and !koreanPattern.isEmpty()\">")
                .contains("OR REGEXP_LIKE(ti.title, #{koreanPattern})")
                .doesNotContain(
                        "ti.content LIKE", "ic.name LIKE", "${keywordPattern}", "${koreanPattern}")
                .doesNotContain("ti.category_id = #{categoryId}");
        assertThat(list)
                .contains("<include refid=\"PublicListFilters\"/>")
                .contains("ORDER BY ti.created_at DESC, ti.id DESC")
                .contains("LIMIT #{limit}")
                .contains("OFFSET #{offset}");
        assertThat(count)
                .contains("SELECT COUNT(*)")
                .contains("JOIN info_categories ic ON ic.id = ti.category_id")
                .contains("<include refid=\"PublicListFilters\"/>")
                .doesNotContain("LIMIT", "ORDER BY");
    }

    @Test
    void publicListUsesScalarThumbnailAndOneDeterministicRepresentativePeriod() throws IOException {
        String query = between(mapper(), "<select id=\"findPublicList\"", "</select>");

        assertThat(query)
                .contains("SELECT ii.image_url")
                .contains("FROM info_images ii")
                .contains("ii.info_id = ti.id")
                .contains("ii.is_main = 1")
                .contains("ORDER BY ii.order_index ASC, ii.id ASC")
                .contains("AS thumbnail_url")
                .doesNotContain("JOIN info_images");
        assertThat(query)
                .contains("LEFT JOIN info_periods representative_period")
                .contains("ON representative_period.id = (")
                .contains("period_candidate.info_id = ti.id")
                .contains("ti.content_type = 'FESTIVAL'")
                .contains("period_candidate.start_date &lt;= CURDATE()")
                .contains("period_candidate.end_date >= CURDATE()")
                .contains("period_candidate.start_date > CURDATE()")
                .contains("period_candidate.end_date &lt; CURDATE()")
                .contains("representative_period.start_date")
                .contains("representative_period.end_date")
                .doesNotContain("MIN(", "MAX(");
        assertThat(countOccurrences(query, "LIMIT 1")).isEqualTo(2);
    }

    @Test
    void publicDetailSelectsOnlyPublicFieldsFromVisibleCategories() throws IOException {
        String query = between(mapper(), "<select id=\"findPublicDetailById\"", "</select>");
        String projection = between(query, "SELECT", "FROM travel_info ti");

        assertThat(query)
                .contains("JOIN info_categories ic ON ic.id = ti.category_id")
                .contains("WHERE ti.id = #{id}")
                .contains("ic.is_visible = 1")
                .contains("ic.name AS category_name")
                .doesNotContain("info_images", "thumbnail_url");
        assertThat(projection)
                .contains("ti.id", "ti.title", "ti.content", "ti.views")
                .contains("ti.created_at", "ti.updated_at")
                .doesNotContain("ti.user_id", "ti.category_id");
    }

    @Test
    void publicBookmarkTargetLocksOnlyVisibleTravelInfoWithoutIncrementingViews()
            throws IOException {
        String query = between(mapper(),
                "<select id=\"findPublicBookmarkTargetForUpdate\"", "</select>");

        assertThat(query)
                .contains("SELECT ti.id")
                .contains("JOIN info_categories ic ON ic.id = ti.category_id")
                .contains("WHERE ti.id = #{id}")
                .contains("ic.is_visible = 1")
                .contains("FOR UPDATE")
                .doesNotContain("views", "content", "SET ti.");
    }

    @Test
    void publicViewIncrementIsAtomicVisibleOnlyAndPreservesContentUpdatedAt() throws IOException {
        String update = between(mapper(), "<update id=\"incrementPublicViews\"", "</update>");

        assertThat(update)
                .contains("UPDATE travel_info ti")
                .contains("JOIN info_categories ic")
                .contains("ic.id = ti.category_id")
                .contains("ic.is_visible = 1")
                .contains("ti.views = ti.views + 1")
                .contains("ti.updated_at = ti.updated_at")
                .contains("WHERE ti.id = #{id}")
                .doesNotContain("FOR UPDATE");
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
    void mapperDoesNotReferenceExcludedDomains() throws IOException {
        assertThat(mapper())
                .doesNotContain("events", "destinations");
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

    private int countOccurrences(String source, String value) {
        return source.split(java.util.regex.Pattern.quote(value), -1).length - 1;
    }
}
