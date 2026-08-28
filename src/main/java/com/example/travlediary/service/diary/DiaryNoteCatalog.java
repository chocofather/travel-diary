package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryNoteColor;
import com.example.travlediary.model.DiaryNoteStyle;
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
 * 라벨 / 떡메모지 디자인 목록.
 * resources/json/diary_notes.json 한 곳만 읽어서 저장 허용 검사와 (나중의) picker 표시에 함께 쓴다.
 * 목록을 두 벌로 두지 않으므로 디자인을 늘려도 코드를 고칠 필요가 없다. (스티커 목록과 같은 방식)
 *
 * <p>스티커 목록과 달리 그림 경로가 없다. 저장되는 것은 code 하나뿐이라
 * 확인할 것도 "아는 code 인가" 하나뿐이다.
 */
@Component
public class DiaryNoteCatalog {

    private static final String MANIFEST_PATH = "/json/diary_notes.json";

    /** code → 디자인. 저장할 때 요청한 style_type 이 아는 값인지 확인하는 데 쓴다. */
    private Map<String, DiaryNoteStyle> byCode = Map.of();
    /** 목록 순서는 manifest 를 그대로 따른다. */
    private List<DiaryNoteStyle> styles = List.of();
    /** code → 색. 모양과 다른 축이라 따로 관리한다. */
    private Map<String, DiaryNoteColor> colorsByCode = Map.of();
    private List<DiaryNoteColor> colors = List.of();

    @PostConstruct
    public void load() {
        try (InputStream manifest = getClass().getResourceAsStream(MANIFEST_PATH)) {
            if (manifest == null) {
                throw new IllegalStateException("라벨/메모지 목록 파일을 찾을 수 없습니다: " + MANIFEST_PATH);
            }
            JsonNode root = new ObjectMapper().readTree(manifest);

            // 색을 먼저 읽는다. 모양의 defaultColor 가 아는 색인지 여기서 바로 확인한다.
            List<DiaryNoteColor> readColors = readColors(root);
            Map<String, DiaryNoteColor> indexedColors = new LinkedHashMap<>();
            for (DiaryNoteColor color : readColors) {
                if (indexedColors.putIfAbsent(color.code(), color) != null) {
                    throw new IllegalStateException("라벨/메모지 색 code 가 겹칩니다: " + color.code());
                }
            }
            this.colors = List.copyOf(readColors);
            this.colorsByCode = Map.copyOf(indexedColors);

            List<DiaryNoteStyle> read = readStyles(root);
            Map<String, DiaryNoteStyle> indexed = new LinkedHashMap<>();
            for (DiaryNoteStyle style : read) {
                if (indexed.putIfAbsent(style.code(), style) != null) {
                    throw new IllegalStateException("라벨/메모지 code 가 겹칩니다: " + style.code());
                }
            }
            this.styles = List.copyOf(read);
            this.byCode = Map.copyOf(indexed);
        } catch (Exception exception) {
            throw new IllegalStateException("라벨/메모지 목록을 읽지 못했습니다.", exception);
        }
    }

    /** 아는 디자인일 때만 돌려준다. 그 밖의 값은 저장 단계에서 막힌다. */
    public Optional<DiaryNoteStyle> find(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(byCode.get(code.strip()));
    }

    /** 전체 디자인. 라벨과 메모지가 manifest 순서대로 한 줄에 들어 있다. */
    public List<DiaryNoteStyle> getStyles() {
        return styles;
    }

    /** 한 갈래만. (LABEL / MEMO) 고르는 자리를 나눌 때 쓴다. */
    public List<DiaryNoteStyle> getStyles(String category) {
        return styles.stream()
                .filter(style -> style.category().equals(category))
                .toList();
    }

    /** 아는 색일 때만 돌려준다. 그 밖의 값은 저장 단계에서 막힌다. */
    public Optional<DiaryNoteColor> findColor(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(colorsByCode.get(code.strip()));
    }

    /** 고를 수 있는 색 전부. manifest 순서 그대로다. */
    public List<DiaryNoteColor> getColors() {
        return colors;
    }

    private List<DiaryNoteColor> readColors(JsonNode root) {
        List<DiaryNoteColor> result = new ArrayList<>();
        for (JsonNode node : root.path("colors")) {
            String code = node.path("code").asText("").strip();
            String label = node.path("label").asText("").strip();
            if (code.isEmpty() || label.isEmpty()) {
                throw new IllegalStateException("라벨/메모지 색 정보가 비어 있습니다: " + node);
            }
            result.add(new DiaryNoteColor(code, label));
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("라벨/메모지 색 목록이 비어 있습니다: " + MANIFEST_PATH);
        }
        return result;
    }

    private List<DiaryNoteStyle> readStyles(JsonNode root) {
        List<DiaryNoteStyle> result = new ArrayList<>();
        for (JsonNode node : root.path("styles")) {
            String code = node.path("code").asText("").strip();
            String category = node.path("category").asText("").strip();
            String label = node.path("label").asText("").strip();
            // 고르는 자리의 보기 글. 적지 않으면 이름을 그대로 보여 준다
            String sample = node.path("sample").asText("").strip();

            if (code.isEmpty() || label.isEmpty()) {
                throw new IllegalStateException("라벨/메모지 정보가 비어 있습니다: " + node);
            }
            /*
              갈래는 둘뿐이다. 모르는 값을 그냥 두면 고르는 자리 어디에도 나오지 않아,
              목록에는 있는데 화면에서는 사라진 디자인이 생긴다. 읽는 자리에서 끊는다.
            */
            if (!DiaryNoteStyle.CATEGORY_LABEL.equals(category)
                    && !DiaryNoteStyle.CATEGORY_MEMO.equals(category)) {
                throw new IllegalStateException("라벨/메모지 갈래가 LABEL / MEMO 가 아닙니다: " + node);
            }
            /*
              색을 고르지 않고 붙였을 때 쓰는 색.
              모르는 색을 적어 두면 그 모양은 붙일 때마다 색이 없는 채로 저장된다.
              읽는 자리에서 끊어 목록끼리 어긋나지 않게 한다.
            */
            String defaultColor = node.path("defaultColor").asText("").strip();
            if (!defaultColor.isEmpty() && !colorsByCode.containsKey(defaultColor)) {
                throw new IllegalStateException(
                        "라벨/메모지 기본색이 색 목록에 없습니다: " + node);
            }

            result.add(new DiaryNoteStyle(code, category, label,
                    sample.isEmpty() ? label : sample,
                    defaultColor.isEmpty() ? null : defaultColor));
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("라벨/메모지 목록이 비어 있습니다: " + MANIFEST_PATH);
        }
        return result;
    }
}
