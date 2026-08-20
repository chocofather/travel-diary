package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiarySticker;
import com.example.travlediary.model.DiaryStickerCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    /**
     * 되풀이해서 그리는 스티커(마스킹테이프)의 조각 정보.
     * 화면에서만 쓰는 값이라 요소에는 완성형 imageUrl 하나만 남고, 여기서 다시 찾는다.
     */
    @Test
    void repeatingStickersExposeTheirPiecesByImageUrl() {
        DiarySticker cat = catalog.find("tape-cat-cream").orElseThrow();

        assertThat(cat.isRepeating()).isTrue();
        assertThat(catalog.getRepeatsByImageUrl().get(cat.imageUrl())).isEqualTo(cat.repeat());

        // 조각도 완성형 그림과 같은 공용 asset 규칙을 따르고 실제 파일이 있다
        for (String piece : new String[]{cat.repeat().leftUrl(),
                cat.repeat().centerUrl(), cat.repeat().rightUrl()}) {
            assertThat(piece).startsWith("/images/diary/stickers/");
            assertThat(ASSET_ROOT.resolve(piece.substring(1))).exists();
        }
    }

    /** 마스킹테이프는 모두 되풀이형이고, 조각 파일도 전부 있어야 한다. */
    @Test
    void everyMaskingTapeIsRepeatingWithRealPieces() {
        List<DiarySticker> tapes = catalog.getCategories().stream()
                .filter(category -> "masking-tape".equals(category.id()))
                .flatMap(category -> category.stickers().stream())
                .toList();

        assertThat(tapes).hasSizeGreaterThanOrEqualTo(20);
        assertThat(tapes).allSatisfy(tape -> {
            assertThat(tape.isRepeating()).as("되풀이 정보: " + tape.id()).isTrue();
            for (String piece : new String[]{tape.repeat().leftUrl(),
                    tape.repeat().centerUrl(), tape.repeat().rightUrl()}) {
                assertThat(piece).startsWith("/images/diary/stickers/");
                assertThat(ASSET_ROOT.resolve(piece.substring(1)))
                        .as("조각 파일: " + piece).exists();
            }
        });
        // 그림 경로로 되찾을 수 있어야 이미 붙여 둔 테이프도 같게 그려진다
        assertThat(catalog.getRepeatsByImageUrl()).hasSize(tapes.size());
    }

    /**
     * 마스킹테이프의 작은 갈래(일반/반투명/클리어).
     * tapeType 을 적지 않은 기존 항목은 모두 일반으로 읽혀야 한다.
     */
    @Test
    void tapeTypeDefaultsToNormalAndSeeThroughOnesAreMarked() {
        assertThat(catalog.find("tape-cat-cream").orElseThrow().tapeType())
                .isEqualTo(DiarySticker.TAPE_NORMAL);
        assertThat(catalog.find("airplane").orElseThrow().tapeType())
                .isEqualTo(DiarySticker.TAPE_NORMAL);
        assertThat(catalog.find("tape-clear-flower").orElseThrow().tapeType())
                .isEqualTo(DiarySticker.TAPE_TRANSLUCENT);
        assertThat(catalog.find("tape-glass-flower").orElseThrow().tapeType())
                .isEqualTo(DiarySticker.TAPE_CLEAR);

        for (String tapeType : new String[]{DiarySticker.TAPE_TRANSLUCENT, DiarySticker.TAPE_CLEAR}) {
            List<DiarySticker> seeThrough = catalog.getCategories().stream()
                    .flatMap(category -> category.stickers().stream())
                    .filter(sticker -> tapeType.equals(sticker.tapeType()))
                    .toList();
            // 비치는 테이프도 같은 되풀이 구조를 쓴다
            assertThat(seeThrough).as(tapeType).hasSizeGreaterThanOrEqualTo(6);
            assertThat(seeThrough).allSatisfy(tape -> {
                assertThat(tape.category()).isEqualTo("masking-tape");
                assertThat(tape.isRepeating()).isTrue();
            });
        }
    }

    /** 마스킹테이프가 아닌 스티커는 지금까지처럼 완성형 그림 한 장으로 그린다. */
    @Test
    void ordinaryStickersHaveNoRepeatInformation() {
        assertThat(catalog.find("airplane").orElseThrow().isRepeating()).isFalse();
        assertThat(catalog.find("heart").orElseThrow().isRepeating()).isFalse();
        assertThat(catalog.getRepeatsByImageUrl().keySet())
                .allSatisfy(url -> assertThat(url)
                        .startsWith("/images/diary/stickers/masking-tape/"));
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
