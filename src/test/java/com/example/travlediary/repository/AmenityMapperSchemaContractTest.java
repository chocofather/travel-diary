package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AmenityMapperSchemaContractTest {

    @Test
    void restaurantAmenityStatementsUseTheTableAndColumnNamesFromTheSchema() throws IOException {
        String schema = schema();
        String restaurantAmenities = between(schema,
                "CREATE TABLE `restaurant_amenities`", ") ENGINE=InnoDB");

        assertThat(restaurantAmenities)
                .contains("`restaurant_id` bigint NOT NULL")
                .contains("`amenity_id` int NOT NULL");

        String mapper = mapper();

        assertThat(mapper)
                .doesNotContain("restaurants_amenities")
                .doesNotContain("restaurants_id");
        assertThat(mapper)
                .contains("INSERT INTO restaurant_amenities (restaurant_id, amenity_id)")
                .contains("FROM restaurant_amenities ra")
                .contains("WHERE ra.restaurant_id = #{destinationId}")
                .contains("DELETE FROM restaurant_amenities WHERE restaurant_id = #{restaurantId}");
    }

    @Test
    void everyAmenityLinkTableUsedByTheMapperExistsInTheSchema() throws IOException {
        String schema = schema();
        String mapper = mapper();

        for (String table : new String[]{
                "attraction_amenities",
                "accommodation_amenities",
                "restaurant_amenities",
                "activity_amenities",
                "shop_amenities"}) {
            assertThat(schema).contains("CREATE TABLE `" + table + "`");
            assertThat(mapper).contains(table);
        }
    }

    @Test
    void everyDeclaredMapperMethodHasAStatement() throws IOException {
        String mapper = mapper();
        String mapperInterface = Files.readString(
                Path.of("src/main/java/com/example/travlediary/repository/amenity/AmenityMapper.java"),
                StandardCharsets.UTF_8);

        // 선언만 있고 XML statement 가 없으면 호출 시 BindingException 이 난다
        for (String id : new String[]{
                "selectAmenityById",
                "countByCode",
                "updateAmenityIconUrl",
                "updateAmenityTranslation",
                "deleteAmenityTranslation",
                "findAmenityDestinationTypesByAmenityId",
                "insertAmenityDestinationType",
                "deleteAmenityDestinationTypesByAmenityId"}) {
            assertThat(mapperInterface).as("interface declares %s", id).contains(id);
            assertThat(mapper).as("xml defines %s", id).contains("id=\"" + id + "\"");
        }
    }

    @Test
    void amenityAndTranslationWriteStatementsMatchTheSchema() throws IOException {
        String schema = schema();
        String mapper = mapper();

        assertThat(between(schema, "CREATE TABLE `amenity_translations`", ") ENGINE=InnoDB"))
                .contains("`amenity_id` int NOT NULL")
                .contains("`language_code` varchar(5) NOT NULL")
                .contains("`name` varchar(100) NOT NULL")
                .contains("UNIQUE KEY `amenity_id` (`amenity_id`,`language_code`)");

        assertThat(between(mapper, "<select id=\"countByCode\"", "</select>"))
                .contains("resultType=\"int\"")
                .contains("SELECT COUNT(*) FROM amenities WHERE code = #{code}");

        // 번역 수정/삭제는 UNIQUE(amenity_id, language_code) 조합을 그대로 키로 쓴다
        assertThat(between(mapper, "<update id=\"updateAmenityTranslation\"", "</update>"))
                .contains("UPDATE amenity_translations")
                .contains("SET name = #{name}")
                .contains("WHERE amenity_id = #{amenityId} AND language_code = #{languageCode}")
                // code 는 아이콘 파일명과 연결되므로 이번 단계에서 amenities UPDATE 는 만들지 않는다
                .doesNotContain("amenities");
        assertThat(between(mapper, "<delete id=\"deleteAmenityTranslation\"", "</delete>"))
                .contains("DELETE FROM amenity_translations")
                .contains("WHERE amenity_id = #{amenityId} AND language_code = #{languageCode}");

        // icon_url 갱신은 허용하지만 code UPDATE 와 amenities 삭제는 아직 만들지 않는다
        assertThat(mapper)
                .doesNotContain("UPDATE amenities SET code")
                .doesNotContain("DELETE FROM amenities");
    }

    @Test
    void iconUrlIsSelectedWhereverTheScreenNeedsAnIcon() throws IOException {
        String schema = schema();
        String mapper = mapper();

        assertThat(between(schema, "CREATE TABLE `amenities`", ") ENGINE=InnoDB"))
                .contains("`icon_url` varchar(255) DEFAULT NULL");

        // 다섯 조회가 함께 쓰는 컬럼 묶음에 icon_url 과 code 가 들어 있다
        assertThat(between(mapper, "<sql id=\"AmenityTranslationColumns\">", "</sql>"))
                .contains("a.code AS code")
                .contains("a.icon_url AS icon_url");

        // 상세 페이지가 쓰는 5개 조회는 amenities.icon_url 을 함께 읽는다
        for (String select : new String[]{
                "findAttractionAmenityTranslationsByDestinationId",
                "findAccommodationAmenityTranslationsByDestinationId",
                "findRestaurantAmenityTranslationsByDestinationId",
                "findActivityAmenityTranslationsByDestinationId",
                "findShopAmenityTranslationsByDestinationId"}) {
            assertThat(between(mapper, "<select id=\"" + select + "\"", "</select>"))
                    .as("icon_url in %s", select)
                    .contains("<include refid=\"AmenityTranslationColumns\"/>");
        }
        assertThat(between(mapper, "<select id=\"selectAmenityById\"", "</select>"))
                .contains("SELECT id, code, icon_url FROM amenities WHERE id = #{id}");

        // 아이콘 경로만 갱신하고 code 는 건드리지 않는다
        assertThat(between(mapper, "<update id=\"updateAmenityIconUrl\"", "</update>"))
                .contains("UPDATE amenities SET icon_url = #{iconUrl} WHERE id = #{id}")
                .doesNotContain("code");
        assertThat(mapper()).doesNotContain("UPDATE amenities SET code");
    }

    @Test
    void existingAmenityStatementsStayUntouched() throws IOException {
        String mapper = mapper();

        assertThat(mapper)
                .contains("SELECT * FROM amenities ORDER BY id ASC")
                .contains("INSERT INTO amenities (code)")
                .contains("INSERT INTO amenity_translations (amenity_id, language_code, name)")
                .contains("<select id=\"findTranslationsByAmenityId\"")
                .contains("<select id=\"findTranslation\"")
                .contains("<select id=\"findAmenityDestinationTypes\"")
                .contains("<select id=\"findTranslationsByDestinationTypeAndLang\"");
    }

    private String schema() throws IOException {
        return Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
    }

    private String mapper() throws IOException {
        return Files.readString(
                Path.of("src/main/resources/mapper/AmenityMapper.xml"), StandardCharsets.UTF_8);
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertThat(startIndex).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
