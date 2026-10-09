package com.tripbora.service.category;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripbora.dto.CountryCategoryForm;
import com.tripbora.model.CountryCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 관리자 지역 일괄 등록. JSON 배열 한 줄이 지역 하나다.
 *
 * <pre>[{"parent": "인도네시아", "ko": "중부 자바", "en": "Central Java", "ja": "...", "zh-CN": "...", "zh-TW": "..."}]</pre>
 *
 * <p>검증과 저장은 단건 등록({@link CountryCategoryAdminService#create})을 그대로 부른다. 그래서 depth 계산,
 * 계층 제한, code 규칙, 같은 부모 아래 이름 중복 차단이 단건 등록과 같다.
 *
 * <p>이 클래스에는 트랜잭션을 걸지 않는다. 주입받은 {@code adminService} 는 프록시라 행마다 따로
 * 트랜잭션이 열리고 닫힌다. 한 행이 실패해도 그 행만 되돌아가고 앞뒤의 정상 행은 등록된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CountryCategoryBulkCreateService {

    static final int MAX_ROWS = 30;

    private static final String PARENT = "parent";
    private static final String CODE = "code";
    /** 이름 필드. JSON 키가 곧 번역 언어 코드다. */
    private static final List<String> NAME_FIELDS = List.of("ko", "en", "ja", "zh-CN", "zh-TW");

    private final CountryCategoryAdminService adminService;
    private final ObjectMapper objectMapper;

    public enum Status {
        CREATED("등록 완료"),
        DUPLICATE("이미 존재"),
        FAILED("실패");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** @param number 1부터 센 행 번호 */
    public record Row(int number, String parent, String name, Status status, String message) {
    }

    /** @param fileError JSON 자체를 읽지 못한 이유. 이때 rows 는 비어 있다 */
    public record Result(String fileError, List<Row> rows) {

        public int total() {
            return rows.size();
        }

        public long created() {
            return count(Status.CREATED);
        }

        public long duplicates() {
            return count(Status.DUPLICATE);
        }

        public long failed() {
            return count(Status.FAILED);
        }

        private long count(Status status) {
            return rows.stream().filter(row -> row.status() == status).count();
        }
    }

    public Result create(String json) {
        if (json == null || json.isBlank()) {
            return fileError("등록할 지역 JSON 을 붙여넣어 주세요.");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            return fileError("JSON 형식이 올바르지 않습니다: " + exception.getOriginalMessage());
        }
        if (root == null || !root.isArray()) {
            return fileError("JSON 배열([ ... ])로 입력해 주세요.");
        }
        if (root.isEmpty()) {
            return fileError("등록할 지역이 없습니다.");
        }
        if (root.size() > MAX_ROWS) {
            return fileError("한 번에 최대 " + MAX_ROWS + "건까지 등록할 수 있습니다. (현재 " + root.size() + "건)");
        }

        // 이 파일에서 등록한 이름. 부모 id → 언어 → 정규화한 이름
        Map<Long, Map<String, Set<String>>> createdNames = new HashMap<>();
        List<Row> rows = new ArrayList<>();
        int number = 0;
        for (JsonNode item : root) {
            number++;
            rows.add(createRow(number, item, createdNames));
        }
        return new Result(null, List.copyOf(rows));
    }

    private Row createRow(int number, JsonNode item, Map<Long, Map<String, Set<String>>> createdNames) {
        if (item == null || !item.isObject()) {
            return new Row(number, null, null, Status.FAILED, "지역 하나는 { ... } 객체로 입력해 주세요.");
        }
        String parentName = text(item, PARENT);
        String name = text(item, "ko");
        String fieldError = fieldError(item);
        if (fieldError != null) {
            return new Row(number, parentName, name, Status.FAILED, fieldError);
        }
        if (parentName == null) {
            return new Row(number, null, name, Status.FAILED, "parent 에 부모 지역 이름을 입력해 주세요.");
        }

        List<CountryCategory> parents = adminService.findParentCandidates(parentName);
        if (parents.isEmpty()) {
            return new Row(number, parentName, name, Status.FAILED,
                    "부모 지역을 찾을 수 없습니다: " + parentName + " (최상위 지역이나 국가·시/도만 부모가 될 수 있습니다)");
        }
        if (parents.size() > 1) {
            return new Row(number, parentName, name, Status.FAILED,
                    "같은 이름의 부모 지역이 여러 곳이라 하나로 정할 수 없습니다: " + parentName);
        }
        Long parentId = parents.get(0).getId();

        Map<String, String> names = new LinkedHashMap<>();
        NAME_FIELDS.forEach(field -> names.put(field, text(item, field)));
        String repeated = repeatedInFile(createdNames.get(parentId), names);
        if (repeated != null) {
            return new Row(number, parentName, name, Status.DUPLICATE, "JSON 안의 앞 행과 같은 이름입니다: " + repeated);
        }

        CountryCategoryForm form = new CountryCategoryForm();
        form.setParentId(parentId);
        form.setRegionName(names.get("ko"));
        form.setNameEn(names.get("en"));
        form.setNameJa(names.get("ja"));
        form.setNameZhCn(names.get("zh-CN"));
        form.setNameZhTw(names.get("zh-TW"));
        form.setCode(text(item, CODE));
        try {
            adminService.create(form);
        } catch (CountryCategoryDuplicateNameException exception) {
            return new Row(number, parentName, name, Status.DUPLICATE, exception.getMessage());
        } catch (CountryCategoryValidationException exception) {
            return new Row(number, parentName, name, Status.FAILED, exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("지역 일괄 등록 중 한 행을 저장하지 못했습니다. (행: {}, 원인: {})",
                    number, exception.getClass().getSimpleName(), exception);
            return new Row(number, parentName, name, Status.FAILED, "저장하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }

        Map<String, Set<String>> taken = createdNames.computeIfAbsent(parentId, key -> new HashMap<>());
        names.forEach((languageCode, value) -> {
            if (value != null) {
                taken.computeIfAbsent(languageCode, key -> new HashSet<>())
                        .add(CountryCategoryAdminService.comparable(value));
            }
        });
        return new Row(number, parentName, name, Status.CREATED, null);
    }

    /** 모르는 필드나 문자열이 아닌 값이 있으면 그 이유. */
    private static String fieldError(JsonNode item) {
        for (Map.Entry<String, JsonNode> field : item.properties()) {
            String key = field.getKey();
            if (!PARENT.equals(key) && !CODE.equals(key) && !NAME_FIELDS.contains(key)) {
                return "알 수 없는 필드입니다: " + key;
            }
            JsonNode value = field.getValue();
            if (value != null && !value.isNull() && !value.isTextual()) {
                return key + " 값은 문자열로 입력해 주세요.";
            }
        }
        return null;
    }

    private static String repeatedInFile(Map<String, Set<String>> taken, Map<String, String> names) {
        if (taken == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : names.entrySet()) {
            String value = entry.getValue();
            if (value != null && taken.getOrDefault(entry.getKey(), Set.of())
                    .contains(CountryCategoryAdminService.comparable(value))) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode item, String field) {
        JsonNode value = item.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            return null;
        }
        return value.asText().strip();
    }

    private static Result fileError(String message) {
        return new Result(message, List.of());
    }
}
