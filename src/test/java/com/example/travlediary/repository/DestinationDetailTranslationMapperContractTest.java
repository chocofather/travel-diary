package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationDetailTranslationMapperContractTest {

    @Test
    void publicDetailLoadsBaseDestinationWithoutRequiringKoreanTranslationJoin() throws IOException {
        String mapper = Files.readString(
                Path.of("src/main/resources/mapper/DestinationMapper.xml"), StandardCharsets.UTF_8);
        String detailQuery = between(mapper,
                "<select id=\"findDestinationDetail\"", "</select>");
        String translationsQuery = between(mapper,
                "<select id=\"findTranslationsByDestinationId\"", "</select>");

        assertThat(detailQuery)
                .contains("FROM destinations d", "WHERE d.id = #{id}")
                .doesNotContain("language_code = 'ko'", "JOIN destination_translations");
        assertThat(translationsQuery)
                .contains("WHERE destination_id = #{destinationId}")
                .doesNotContain("${");
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
