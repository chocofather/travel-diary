package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SitemapMapperContractTest {

    @Test
    void sitemapQueriesExcludeUnrenderableAndHiddenPublicContent() throws IOException {
        String xml;
        try (var input = getClass().getResourceAsStream("/mapper/SitemapMapper.xml")) {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml)
                .contains("FROM destination_translations dt")
                .contains("ic.is_visible = 1")
                .contains("p.deleted = 0")
                .contains("c.deleted = 0")
                .contains("cm.status = 'ACTIVE'");
    }
}
