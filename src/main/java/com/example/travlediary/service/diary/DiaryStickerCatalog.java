package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiarySticker;
import com.example.travlediary.model.DiaryStickerCategory;
import com.example.travlediary.model.DiaryStickerRepeat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 공용 스티커 목록.
 * resources/json/diary_stickers.json 한 곳만 읽어서 picker 표시와 저장 허용 검사에 함께 쓴다.
 * (목록을 두 벌로 두지 않으므로 스티커를 늘려도 코드를 고칠 필요가 없다)
 */
@Component
public class DiaryStickerCatalog {

    private static final String MANIFEST_PATH = "/json/diary_stickers.json";
    /** 공용 asset 디렉터리. 이 아래 경로만 스티커로 저장할 수 있다. (외부 주소 저장 금지) */
    private static final String ASSET_PREFIX = "/images/diary/stickers/";
    /** 가운데 무늬만 가로로 되풀이해서 그리는 방식 (마스킹테이프) */
    private static final String REPEAT_X = "REPEAT_X";

    /** picker 에 보여줄 묶음. 목록 순서는 manifest 를 그대로 따른다. */
    private List<DiaryStickerCategory> categories = List.of();
    /** id → 스티커. 저장할 때 요청한 id 가 아는 값인지 확인하는 데 쓴다. */
    private Map<String, DiarySticker> byId = Map.of();
    /** 그림 경로 → 되풀이 조각. 저장된 요소를 화면에 그릴 때만 쓴다. */
    private Map<String, DiaryStickerRepeat> repeatsByImageUrl = Map.of();

    @PostConstruct
    public void load() {
        try (InputStream manifest = getClass().getResourceAsStream(MANIFEST_PATH)) {
            if (manifest == null) {
                throw new IllegalStateException("스티커 목록 파일을 찾을 수 없습니다: " + MANIFEST_PATH);
            }
            JsonNode root = new ObjectMapper().readTree(manifest);
            Map<String, DiarySticker> stickers = readStickers(root);
            this.byId = Map.copyOf(stickers);
            this.categories = List.copyOf(readCategories(root, stickers.values()));

            Map<String, DiaryStickerRepeat> repeats = new LinkedHashMap<>();
            stickers.values().stream()
                    .filter(DiarySticker::isRepeating)
                    .forEach(sticker -> repeats.put(sticker.imageUrl(), sticker.repeat()));
            this.repeatsByImageUrl = Map.copyOf(repeats);
        } catch (Exception exception) {
            throw new IllegalStateException("스티커 목록을 읽지 못했습니다.", exception);
        }
    }

    /** 아는 스티커일 때만 돌려준다. 그 밖의 값(외부 주소 포함)은 저장 단계에서 막힌다. */
    public Optional<DiarySticker> find(String stickerId) {
        return stickerId == null ? Optional.empty() : Optional.ofNullable(byId.get(stickerId.strip()));
    }

    /** picker 가 그대로 그리는 묶음 목록. 스티커가 없는 묶음은 빼고 준다. */
    public List<DiaryStickerCategory> getCategories() {
        return categories;
    }

    /**
     * 그림 경로 → 되풀이 조각. 이미 붙여 둔 요소는 저장된 image_url 만 들고 있으므로
     * 화면을 그릴 때 이 표에서 다시 찾는다. (DB 에는 조각 경로를 넣지 않는다)
     */
    public Map<String, DiaryStickerRepeat> getRepeatsByImageUrl() {
        return repeatsByImageUrl;
    }

    private Map<String, DiarySticker> readStickers(JsonNode root) {
        Map<String, DiarySticker> stickers = new LinkedHashMap<>();
        for (JsonNode node : root.path("stickers")) {
            String id = node.path("id").asText("").strip();
            String name = node.path("name").asText("").strip();
            String category = node.path("category").asText("").strip();
            String imageUrl = node.path("imageUrl").asText("").strip();

            if (id.isEmpty() || name.isEmpty() || category.isEmpty()) {
                throw new IllegalStateException("스티커 정보가 비어 있습니다: " + node);
            }
            DiarySticker sticker = new DiarySticker(id, name, category,
                    assetUrl(imageUrl), tapeTypeOf(node), repeatOf(node));
            if (stickers.putIfAbsent(id, sticker) != null) {
                throw new IllegalStateException("스티커 id 가 겹칩니다: " + id);
            }
        }
        return stickers;
    }

    /**
     * 마스킹테이프 안의 작은 갈래(일반/반투명/클리어).
     * 적혀 있지 않거나 모르는 값이면 기존 항목처럼 일반 테이프로 본다.
     */
    private String tapeTypeOf(JsonNode node) {
        String tapeType = node.path("tapeType").asText("").strip().toUpperCase();
        if (DiarySticker.TAPE_TRANSLUCENT.equals(tapeType)
                || DiarySticker.TAPE_CLEAR.equals(tapeType)) {
            return tapeType;
        }
        return DiarySticker.TAPE_NORMAL;
    }

    /**
     * 되풀이해서 그리는 스티커의 조각 경로. (renderMode = REPEAT_X 일 때만)
     * 적혀 있지 않으면 지금까지처럼 완성형 그림 한 장으로 그린다.
     */
    private DiaryStickerRepeat repeatOf(JsonNode node) {
        if (!REPEAT_X.equalsIgnoreCase(node.path("renderMode").asText("").strip())) {
            return null;
        }
        JsonNode repeat = node.path("repeat");
        return new DiaryStickerRepeat(
                assetUrl(repeat.path("left").asText("").strip()),
                assetUrl(repeat.path("center").asText("").strip()),
                assetUrl(repeat.path("right").asText("").strip()));
    }

    /** 목록 파일이 잘못돼도 공용 asset 바깥을 가리키지 못하게 한다. (조각 경로도 같은 규칙) */
    private String assetUrl(String url) {
        if (!url.startsWith(ASSET_PREFIX) || url.contains("..")) {
            throw new IllegalStateException("스티커 경로가 공용 asset 밖입니다: " + url);
        }
        return url;
    }

    private List<DiaryStickerCategory> readCategories(JsonNode root,
                                                      Iterable<DiarySticker> stickers) {
        List<DiaryStickerCategory> result = new ArrayList<>();
        for (JsonNode node : root.path("categories")) {
            String id = node.path("id").asText("").strip();
            String name = node.path("name").asText("").strip();
            if (id.isEmpty() || name.isEmpty()) {
                throw new IllegalStateException("스티커 분류 정보가 비어 있습니다: " + node);
            }

            List<DiarySticker> owned = new ArrayList<>();
            for (DiarySticker sticker : stickers) {
                if (id.equals(sticker.category())) {
                    owned.add(sticker);
                }
            }
            // 아직 스티커를 넣지 않은 묶음은 picker 에 빈 탭으로 보여주지 않는다.
            if (!owned.isEmpty()) {
                result.add(new DiaryStickerCategory(id, name, List.copyOf(owned)));
            }
        }
        return result;
    }
}
