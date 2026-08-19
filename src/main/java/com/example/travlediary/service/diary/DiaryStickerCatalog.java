package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiarySticker;
import com.example.travlediary.model.DiaryStickerCategory;
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

    /** picker 에 보여줄 묶음. 목록 순서는 manifest 를 그대로 따른다. */
    private List<DiaryStickerCategory> categories = List.of();
    /** id → 스티커. 저장할 때 요청한 id 가 아는 값인지 확인하는 데 쓴다. */
    private Map<String, DiarySticker> byId = Map.of();

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
            // 목록 파일이 잘못돼도 공용 asset 바깥을 가리키지 못하게 한다.
            if (!imageUrl.startsWith(ASSET_PREFIX) || imageUrl.contains("..")) {
                throw new IllegalStateException("스티커 경로가 공용 asset 밖입니다: " + imageUrl);
            }
            if (stickers.putIfAbsent(id, new DiarySticker(id, name, category, imageUrl)) != null) {
                throw new IllegalStateException("스티커 id 가 겹칩니다: " + id);
            }
        }
        return stickers;
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
