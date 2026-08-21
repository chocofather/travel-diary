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
