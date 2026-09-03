package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AttractionInfoSchemaContractTest {

    @Test
    void attractionNaturalLanguageColumnsMatchTheTourApiStorageContract() throws IOException {
        String schema = schema();
        String attractionInfo = between(schema,
                "CREATE TABLE `attraction_info`", ") ENGINE=InnoDB");

        assertThat(attractionInfo)
                .contains("`closed_days` varchar(500) DEFAULT NULL")
                .contains("`opening_hours` varchar(1000) DEFAULT NULL")
                .contains("`admission_fee` text DEFAULT NULL")
                .contains("`contact_number` varchar(255) DEFAULT NULL")
                .contains("`homepage_url` varchar(255) DEFAULT NULL")
                .contains("`guide` text DEFAULT NULL");
    }

    @Test
    void activityAndShopAutofillColumnsUseTheSameNaturalLanguageCapacity() throws IOException {
        String schema = schema();
        String activityInfo = between(schema,
                "CREATE TABLE `activity_info`", ") ENGINE=InnoDB");
        String shopInfo = between(schema,
                "CREATE TABLE `shop_info`", ") ENGINE=InnoDB");

        assertThat(activityInfo)
                .contains("`opening_hours` varchar(1000) DEFAULT NULL")
                .contains("`admission_fee` text DEFAULT NULL")
                .contains("`contact_number` varchar(255) DEFAULT NULL")
                .contains("`homepage_url` varchar(255) DEFAULT NULL")
                .contains("`guide` text DEFAULT NULL");
        assertThat(shopInfo)
                .contains("`closed_days` varchar(500) DEFAULT NULL")
                .contains("`opening_hours` varchar(1000) DEFAULT NULL")
                .contains("`contact_number` varchar(255) DEFAULT NULL")
                .contains("`homepage_url` varchar(255) DEFAULT NULL")
                .contains("`guide` text DEFAULT NULL");
    }

    @Test
    void attractionTranslationsMatchTheLiveDatabaseContract() throws IOException {
        String schema = schema();
        String translations = between(schema,
                "CREATE TABLE `attraction_info_translations`",
                "/*!40101 SET character_set_client = @saved_cs_client */;");

        assertThat(translations)
                .contains("`id` bigint NOT NULL AUTO_INCREMENT")
                .contains("`destination_id` bigint NOT NULL")
                .contains("`language_code` varchar(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL")
                .contains("`closed_days` varchar(500) DEFAULT NULL")
                .contains("`opening_hours` varchar(1000) DEFAULT NULL")
                .contains("`admission_fee` text DEFAULT NULL")
                .contains("`guide` text DEFAULT NULL")
                .contains("UNIQUE KEY `uq_attraction_info_translations_destination_language` (`destination_id`,`language_code`)")
                .contains("KEY `idx_attraction_info_translations_language_destination` (`language_code`,`destination_id`)")
                .contains("FOREIGN KEY (`destination_id`) REFERENCES `attraction_info` (`destination_id`) ON DELETE CASCADE")
                .contains(
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");
    }

    private String schema() throws IOException {
        return Files.readString(
                Path.of("docs/db/travel_diary_schema_reference.md"), StandardCharsets.UTF_8);
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertThat(startIndex).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
