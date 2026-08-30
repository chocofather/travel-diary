package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryLabelFont;
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
 * 라벨기(TEXT 요소) 글꼴 목록.
 * resources/json/diary_label_fonts.json 한 곳만 읽어서 저장 허용 검사와 picker 표시에 함께 쓴다.
 * 목록을 두 벌로 두지 않으므로 글꼴을 늘려도 코드를 고칠 필요가 없다.
 * (스티커·라벨/메모지 목록과 같은 방식)
 *
 * <p>페이지 다꾸와 커스텀 표지가 같은 목록을 함께 쓴다. 두 편집기의 글꼴이 어긋나지 않는다.
 */
@Component
public class DiaryLabelFontCatalog {

    private static final String MANIFEST_PATH = "/json/diary_label_fonts.json";
    /** 저장할 수 있는 code 모양. 그대로 class 이름이 되는 값이라 여기서 한 번 가린다. */
    private static final String CODE_PATTERN = "[a-z0-9-]+";

    /** code → 글꼴. 저장할 때 요청한 값이 아는 글꼴인지 확인하는 데 쓴다. */
    private Map<String, DiaryLabelFont> byCode = Map.of();
    /** 목록 순서는 manifest 를 그대로 따른다. */
    private List<DiaryLabelFont> fonts = List.of();

    @PostConstruct
    public void load() {
        try (InputStream manifest = getClass().getResourceAsStream(MANIFEST_PATH)) {
            if (manifest == null) {
                throw new IllegalStateException("라벨 글꼴 목록 파일을 찾을 수 없습니다: " + MANIFEST_PATH);
            }
            JsonNode root = new ObjectMapper().readTree(manifest);

            List<DiaryLabelFont> read = readFonts(root);
            Map<String, DiaryLabelFont> indexed = new LinkedHashMap<>();
            for (DiaryLabelFont font : read) {
                if (indexed.putIfAbsent(font.code(), font) != null) {
                    throw new IllegalStateException("라벨 글꼴 code 가 겹칩니다: " + font.code());
                }
            }
            this.fonts = List.copyOf(read);
            this.byCode = Map.copyOf(indexed);
        } catch (Exception exception) {
            throw new IllegalStateException("라벨 글꼴 목록을 읽지 못했습니다.", exception);
        }
    }

    /** 아는 글꼴일 때만 돌려준다. 그 밖의 값은 저장 단계에서 막힌다. */
    public Optional<DiaryLabelFont> find(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(byCode.get(code.strip()));
    }

    /** 고를 수 있는 글꼴 전부. manifest 순서 그대로다. */
    public List<DiaryLabelFont> getFonts() {
        return fonts;
    }

    private List<DiaryLabelFont> readFonts(JsonNode root) {
        List<DiaryLabelFont> result = new ArrayList<>();
        for (JsonNode node : root.path("fonts")) {
            String code = node.path("code").asText("").strip();
            String label = node.path("label").asText("").strip();
            if (code.isEmpty() || label.isEmpty()) {
                throw new IllegalStateException("라벨 글꼴 정보가 비어 있습니다: " + node);
            }
            /*
              code 는 그대로 diary-font-{code} class 가 된다.
              모양이 어긋난 값을 그냥 두면 저장은 되는데 화면에서는 글꼴이 붙지 않는다.
              읽는 자리에서 끊어 목록과 화면이 어긋나지 않게 한다.
            */
            if (!code.matches(CODE_PATTERN)) {
                throw new IllegalStateException(
                        "라벨 글꼴 code 는 소문자-하이픈만 쓸 수 있습니다: " + code);
            }
            result.add(new DiaryLabelFont(code, label));
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("라벨 글꼴 목록이 비어 있습니다: " + MANIFEST_PATH);
        }
        return result;
    }
}
