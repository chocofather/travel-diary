package com.example.travlediary.service.travelinfo.structured;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;

/**
 * 구조화 콘텐츠 JSON 전용 mapper. 읽기({@link StructuredContentParser})와 쓰기({@link StructuredContentSerializer})가
 * 같은 설정을 쓴다. Spring 의 공용 ObjectMapper 설정과 섞이지 않게 따로 둔다.
 *
 * <p>읽기는 엄격하다.
 * <ul>
 *   <li>모르는 필드, 등록되지 않은 블록 type, type 이 없는 블록은 거부한다. (조용히 버리고 저장하지 않는다)</li>
 *   <li>같은 키가 두 번 나오거나 JSON 뒤에 다른 값이 붙어 있으면 거부한다.</li>
 *   <li>"100" → 100, 1.5 → 1, 1 → "1", 0 → 첫 enum 같은 자동 변환을 하지 않는다.</li>
 * </ul>
 * 쓰기는 값이 없는 칸을 빼고, 번역 map 은 키 순서로 적는다. 같은 내용이면 언제나 같은 문자열이 나온다.
 */
final class StructuredJson {

    static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .withCoercionConfig(LogicalType.Textual, config -> config
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .defaultPropertyInclusion(JsonInclude.Value.construct(
                    JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    private StructuredJson() {
    }

    /** 글 칸의 공백뿐인 값은 값이 없는 것으로 맞춘다. (필수 여부는 validator 가 본다) */
    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
