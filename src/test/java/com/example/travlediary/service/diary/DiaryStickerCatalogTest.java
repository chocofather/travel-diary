package com.example.travlediary.service.diary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 기존 static 카탈로그가 DB 초기 데이터로 손실 없이 옮겨지는지 확인한다. */
class DiaryStickerCatalogTest {
    private static final Path MANIFEST = Path.of("src/main/resources/json/diary_stickers.json");
    private static final Path MIGRATION = Path.of("docs/db/diary_sticker_catalog_migration.sql");
    private static final Path STATIC_ROOT = Path.of("src/main/resources/static");

    @Test
    void migrationKeepsEveryLegacyCatalogKeyAndImageUrl() throws Exception {
        JsonNode stickers = new ObjectMapper().readTree(MANIFEST.toFile()).path("stickers");
        String sql = Files.readString(MIGRATION);

        assertThat(stickers).hasSize(235);
        for (JsonNode sticker : stickers) {
            String key = sticker.path("id").asText();
            String imageUrl = sticker.path("imageUrl").asText();
            assertThat(sql).as(key).contains("('" + key.replace("'", "''") + "'");
            assertThat(sql).as(imageUrl).contains("'" + imageUrl.replace("'", "''") + "'");
            assertThat(STATIC_ROOT.resolve(imageUrl.substring(1))).as(imageUrl).exists();
        }
    }

    @Test
    void migrationKeepsEveryLegacyRepeatPiece() throws Exception {
        JsonNode stickers = new ObjectMapper().readTree(MANIFEST.toFile()).path("stickers");
        String sql = Files.readString(MIGRATION);

        for (JsonNode sticker : stickers) {
            JsonNode repeat = sticker.path("repeat");
            for (String side : new String[]{"left", "center", "right"}) {
                if (!repeat.hasNonNull(side)) continue;
                String imageUrl = repeat.path(side).asText();
                assertThat(sql).as(sticker.path("id").asText() + " " + side)
                        .contains("'" + imageUrl.replace("'", "''") + "'");
                assertThat(STATIC_ROOT.resolve(imageUrl.substring(1))).as(imageUrl).exists();
            }
        }
    }
}
