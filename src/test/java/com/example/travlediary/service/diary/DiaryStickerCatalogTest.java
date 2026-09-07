package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiarySticker;
import com.example.travlediary.model.DiaryStickerCategory;
import com.example.travlediary.model.DiaryStickerCollection;
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

    /* === 표현 스타일(collection) === */

    /**
     * 목록에 collection 을 적지 않은 스티커는 모두 기본 묶음이다.
     * (기존 216줄에 값을 넣지 않아도 그대로 읽힌다)
     */
    @Test
    void stickersWithoutACollectionAreReadAsTheDefaultOne() {
        assertThat(catalog.getCategories())
                .flatExtracting(DiaryStickerCategory::stickers)
                .filteredOn(sticker -> !sticker.imageUrl().contains("/realistic/"))
                .isNotEmpty()
                .allSatisfy(sticker -> {
                    assertThat(sticker.collection()).isEqualTo(DiaryStickerCollection.DEFAULT);
                    assertThat(sticker.collectionCode()).isEqualTo("default");
                });
    }

    /**
     * 리얼 묶음의 랜드마크 19개는 자기 분류(랜드마크) 안에 모여 있다.
     * asset 은 옮기지 않았으므로 그림 경로는 그대로 travel/realistic/ 이다.
     */
    @Test
    void theRealisticLandmarksLiveInsideTheLandmarkCategory() {
        assertThat(realisticStickers())
                .extracting(DiarySticker::id)
                .containsExactlyInAnyOrder(
                        "gyeongbokgung", "golden-gate-bridge", "neuschwanstein-castle",
                        "great-wall-of-china", "big-ben", "sagrada-familia", "seoul-n-tower",
                        "st-basils-cathedral", "sydney-opera-house", "angkor-wat",
                        "eiffel-tower", "osaka-castle", "statue-of-liberty", "colosseum",
                        "taipei-101", "taj-mahal", "petra", "leaning-tower-of-pisa",
                        "mount-fuji");

        assertThat(realisticStickers()).allSatisfy(sticker -> {
            assertThat(sticker.category()).isEqualTo("landmark");
            assertThat(sticker.collectionCode()).isEqualTo("realistic");
            // 파일 이름은 id 와 같다. (id 만 보고 asset 을 찾을 수 있게)
            assertThat(sticker.imageUrl())
                    .isEqualTo("/images/diary/stickers/travel/realistic/" + sticker.id() + ".png");
        });

        // 주소를 잘못 베껴 두 스티커가 같은 그림을 가리키는 일을 막는다
        assertThat(realisticStickers()).extracting(DiarySticker::imageUrl).doesNotHaveDuplicates();
    }

    /** 랜드마크 분류는 리얼 랜드마크만 담는다. 기존 여행 소품은 여행 분류에 그대로 남는다. */
    @Test
    void theLandmarkCategoryHoldsOnlyLandmarksAndTravelKeepsItsOwnStickers() {
        assertThat(categoryOf("landmark").stickers())
                .hasSize(19)
                .allSatisfy(sticker ->
                        assertThat(sticker.collection()).isEqualTo(DiaryStickerCollection.REALISTIC));

        assertThat(categoryOf("travel").stickers())
                .extracting(DiarySticker::id)
                .contains("airplane", "suitcase", "camera", "passport", "map", "train", "car")
                .doesNotContain("eiffel-tower", "mount-fuji", "gyeongbokgung");
        assertThat(categoryOf("travel").stickers())
                .allSatisfy(sticker ->
                        assertThat(sticker.collection()).isEqualTo(DiaryStickerCollection.DEFAULT));
    }

    /** picker 의 분류 차례는 목록 파일 순서 그대로다. 랜드마크는 여행 바로 뒤에 온다. */
    @Test
    void theLandmarkCategoryComesRightAfterTravel() {
        assertThat(catalog.getCategories())
                .extracting(DiaryStickerCategory::id)
                .containsExactly("travel", "landmark", "emotion", "food", "nature",
                        "weather", "decoration", "lettering", "season", "masking-tape");
        assertThat(categoryOf("landmark").name()).isEqualTo("랜드마크");
    }

    private DiaryStickerCategory categoryOf(String id) {
        return catalog.getCategories().stream()
                .filter(category -> id.equals(category.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("분류가 없습니다: " + id));
    }

    /**
     * 리얼 랜드마크만 PNG 를 쓴다. 지금까지의 기본 스티커는 그대로 SVG 다.
     * (한쪽 형식으로 통일하려다 반대쪽 asset 을 건드리는 일을 막는다)
     */
    @Test
    void onlyTheRealisticLandmarksUsePngAssets() {
        assertThat(catalog.getCategories())
                .flatExtracting(DiaryStickerCategory::stickers)
                .filteredOn(sticker -> sticker.collection() == DiaryStickerCollection.DEFAULT)
                .isNotEmpty()
                .allSatisfy(sticker -> assertThat(sticker.imageUrl()).endsWith(".svg"));
    }

    private List<DiarySticker> realisticStickers() {
        return catalog.getCategories().stream()
                .flatMap(category -> category.stickers().stream())
                .filter(sticker -> sticker.collection() == DiaryStickerCollection.REALISTIC)
                .toList();
    }

    /**
     * 묶음이 실제로 어떤 스타일을 갖고 있는지는 asset 을 갈라 두기 위한 내부 값이다.
     * (화면은 이 값으로 고르는 줄을 그리지 않는다 — picker 는 분류 하나만 나눈다)
     */
    @Test
    void aCategoryOnlyReportsTheCollectionsItActuallyHas() {
        assertThat(catalog.getCategories())
                .filteredOn(category -> !category.id().equals("landmark"))
                .isNotEmpty()
                .allSatisfy(category -> assertThat(category.collections())
                        .containsExactly(DiaryStickerCollection.DEFAULT));

        assertThat(catalog.getCategories())
                .filteredOn(category -> category.id().equals("landmark"))
                .singleElement()
                .satisfies(category -> assertThat(category.collections())
                        .containsExactly(DiaryStickerCollection.REALISTIC));
    }

    /** 목록 파일이 쓰는 code 와 화면이 읽는 값이 같다. 적지 않으면 기본이다. */
    @Test
    void collectionCodesAreReadLenientlyButUnknownOnesAreRejected() {
        assertThat(DiaryStickerCollection.fromCode(null))
                .contains(DiaryStickerCollection.DEFAULT);
        assertThat(DiaryStickerCollection.fromCode("   "))
                .contains(DiaryStickerCollection.DEFAULT);
        assertThat(DiaryStickerCollection.fromCode("default"))
                .contains(DiaryStickerCollection.DEFAULT);
        assertThat(DiaryStickerCollection.fromCode(" REALISTIC "))
                .contains(DiaryStickerCollection.REALISTIC);
        // 오타는 조용히 기본으로 바뀌지 않는다. 목록을 고칠 때 바로 알아채야 한다.
        assertThat(DiaryStickerCollection.fromCode("realstic")).isEmpty();
        assertThat(DiaryStickerCollection.fromCode("cute")).isEmpty();
    }

    /** 표현 스타일과 테이프 갈래는 서로 다른 축이다. 한쪽이 다른 쪽을 대신하지 않는다. */
    @Test
    void theCollectionAxisIsSeparateFromTheMaskingTapeKindAxis() {
        DiarySticker tape = catalog.getCategories().stream()
                .filter(category -> "masking-tape".equals(category.id()))
                .flatMap(category -> category.stickers().stream())
                .filter(sticker -> "TRANSLUCENT".equals(sticker.tapeType()))
                .findFirst()
                .orElseThrow();

        assertThat(tape.collection()).isEqualTo(DiaryStickerCollection.DEFAULT);
        assertThat(tape.tapeType()).isEqualTo("TRANSLUCENT");
        assertThat(tape.isRepeating()).isTrue();
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
