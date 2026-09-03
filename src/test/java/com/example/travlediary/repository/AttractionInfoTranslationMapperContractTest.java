package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AttractionInfoTranslationMapperContractTest {

    @Test
    void translationRowsAreLoadedByDestinationInDeterministicOrder() throws IOException {
        String mapper = Files.readString(
                Path.of("src/main/resources/mapper/AttractionInfoMapper.xml"), StandardCharsets.UTF_8);
        String query = between(mapper,
                "<select id=\"findTranslationsByDestinationId\"", "</select>");

        assertThat(query)
                .contains("FROM attraction_info_translations")
                .contains("WHERE destination_id = #{destinationId}")
                .contains("language_code", "closed_days", "opening_hours", "admission_fee", "guide")
                .contains("ORDER BY language_code ASC, id ASC")
                .doesNotContain("${");
    }

    @Test
    void addingTranslationReadDoesNotChangeBaseCrudStatements() throws IOException {
        String mapper = Files.readString(
                Path.of("src/main/resources/mapper/AttractionInfoMapper.xml"), StandardCharsets.UTF_8);

        assertThat(mapper)
                .contains("INSERT INTO attraction_info")
                .contains("SELECT * FROM attraction_info WHERE destination_id = #{destinationId}")
                .contains("UPDATE attraction_info");
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
