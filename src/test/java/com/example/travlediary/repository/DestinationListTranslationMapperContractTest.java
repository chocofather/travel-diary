package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationListTranslationMapperContractTest {

    @Test
    void destinationTranslationsCanBeLoadedForAPageInOneDeterministicBatch() throws IOException {
        String xml = mapperXml();

        assertThat(xml)
                .contains("<select id=\"findTranslationsByDestinationIds\"")
                .contains("FROM destination_translations")
                .contains("<foreach collection=\"destinationIds\"")
                .contains("#{destinationId}")
                .contains("destinationIds != null and !destinationIds.isEmpty()")
                .contains("WHERE 1 = 0")
                .contains("ORDER BY destination_id, language_code, id")
                .doesNotContain("${destinationIds}");
    }

    private String mapperXml() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/mapper/DestinationMapper.xml")) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
