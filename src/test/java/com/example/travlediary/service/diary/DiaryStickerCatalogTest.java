package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiarySticker;
import com.example.travlediary.model.DiaryStickerCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스티커 목록은 resources/json/diary_stickers.json 한 곳이 기준이다.
 * 여기에 없는 값(외부 주소 포함)은 저장할 수 없고, 목록에 적은 파일은 실제로 있어야 한다.
 */
class DiaryStickerCatalogTest {

    private static final Path ASSET_ROOT = Path.of("src/main/resources/static");

    private DiaryStickerCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new DiaryStickerCatalog();
        catalog.load();
    }

    @Test
    void manifestIsGroupedIntoCategoriesForThePicker() {
        assertThat(catalog.getCategories()).isNotEmpty();
        assertThat(catalog.getCategories())
                .extracting(DiaryStickerCategory::id)
                .startsWith("travel");
        // 빈 묶음은 picker 에 탭으로 나오지 않는다
        assertThat(catalog.getCategories())
                .allSatisfy(category -> assertThat(category.stickers()).isNotEmpty());
    }

    @Test
    void everyStickerFileInTheManifestExists() {
        catalog.getCategories().forEach(category ->
                category.stickers().forEach(sticker -> {
                    // 경로는 언제나 공용 asset 디렉터리 안이다
                    assertThat(sticker.imageUrl())
                            .as("스티커 경로: " + sticker.id())
                            .startsWith("/images/diary/stickers/");
                    assertThat(ASSET_ROOT.resolve(sticker.imageUrl().substring(1)))
                            .as("스티커 파일: " + sticker.id())
                            .exists();
                }));
    }

    @Test
    void onlyKnownStickerIdsResolveToAnImagePath() {
        DiarySticker known = catalog.getCategories().get(0).stickers().get(0);

        assertThat(catalog.find(known.id())).contains(known);
        assertThat(catalog.find("  " + known.id() + "  ")).contains(known);
        // 임의의 외부 주소나 모르는 id 는 통과하지 못한다
        assertThat(catalog.find("https://evil.example.com/tracker.svg")).isEmpty();
        assertThat(catalog.find("../../../etc/passwd")).isEmpty();
        assertThat(catalog.find("")).isEmpty();
        assertThat(catalog.find(null)).isEmpty();
    }

    /** 기존 DB 에 저장된 예전 경로의 스티커도 계속 보이도록 파일을 지우지 않는다. */
    @Test
    void previouslySavedStickerAssetsAreStillServed() throws Exception {
        Path legacy = ASSET_ROOT.resolve("images/diary-stickers");

        assertThat(legacy).exists();
        try (var files = Files.list(legacy)) {
            assertThat(files).isNotEmpty();
        }
    }
}
