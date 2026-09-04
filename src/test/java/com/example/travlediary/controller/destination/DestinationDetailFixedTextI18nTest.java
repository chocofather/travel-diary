package com.example.travlediary.controller.destination;

import com.example.travlediary.config.i18n.SupportedLanguage;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여행지 상세의 고정 문구(가능/불가, 포함/미포함, 필요/불필요)는 화면에 박지 않고 번들에서 온다.
 *
 * <p>다섯 유형이 같은 키를 쓰므로, 값은 유형별로 다르게 나오지 않는다.
 * Boolean/null 판정은 예전 그대로이고 보여 줄 글자만 언어를 따른다.
 */
class DestinationDetailFixedTextI18nTest {

    private static final String DETAIL = "/templates/destination/detail.html";

    private final SpringTemplateEngine engine = newEngine();

    /** 주차/반려동물/포장/배달/예약 가능 여부 (관광지·숙소·식당·체험·쇼핑 공통) */
    private static final String AVAILABLE = "destination.detail.value.available";
    private static final String UNAVAILABLE = "destination.detail.value.unavailable";
    /** 사전 예약 필요 여부 (체험) */
    private static final String REQUIRED = "destination.detail.value.required";
    private static final String NOT_REQUIRED = "destination.detail.value.notRequired";
    /** 조식 포함 여부 (숙소). 한국어는 '불포함' 이라 장비 쪽과 문구를 나눠 둔다. */
    private static final String BREAKFAST_INCLUDED =
            "destination.detail.accommodation.breakfast.included";
    private static final String BREAKFAST_NOT_INCLUDED =
            "destination.detail.accommodation.breakfast.notIncluded";
    /** 장비 대여/포함 여부 (체험). 한국어는 '미포함'. */
    private static final String EQUIPMENT_INCLUDED =
            "destination.detail.activity.equipment.included";
    private static final String EQUIPMENT_NOT_INCLUDED =
            "destination.detail.activity.equipment.notIncluded";

    @Test
    void everyBooleanValueIsReadFromTheBundleInAllFiveLanguages() {
        Map<SupportedLanguage, List<String>> expected = new LinkedHashMap<>();
        //                                가능 / 불가 / 필요 / 불필요
        expected.put(SupportedLanguage.KOREAN,
                List.of("가능", "불가", "필요", "불필요"));
        expected.put(SupportedLanguage.ENGLISH,
                List.of("Available", "Unavailable", "Required", "Not required"));
        expected.put(SupportedLanguage.JAPANESE,
                List.of("利用可", "利用不可", "必要", "不要"));
        expected.put(SupportedLanguage.CHINESE_SIMPLIFIED,
                List.of("可用", "不可用", "需要", "不需要"));
        expected.put(SupportedLanguage.CHINESE_TRADITIONAL,
                List.of("可使用", "不可使用", "需要", "不需要"));

        for (Map.Entry<SupportedLanguage, List<String>> entry : expected.entrySet()) {
            SupportedLanguage language = entry.getKey();
            List<String> words = entry.getValue();

            assertThat(renderBoolean(language, true, AVAILABLE, UNAVAILABLE))
                    .as("%s available", language).isEqualTo(words.get(0));
            assertThat(renderBoolean(language, false, AVAILABLE, UNAVAILABLE))
                    .as("%s unavailable", language).isEqualTo(words.get(1));
            assertThat(renderBoolean(language, true, REQUIRED, NOT_REQUIRED))
                    .as("%s required", language).isEqualTo(words.get(2));
            assertThat(renderBoolean(language, false, REQUIRED, NOT_REQUIRED))
                    .as("%s notRequired", language).isEqualTo(words.get(3));
        }
    }

    /**
     * 조식(숙소)과 장비(체험)는 문맥이 달라 문구를 따로 둔다.
     * 한국어만 '불포함' / '미포함' 으로 갈리고, 나머지 언어는 각자 자연스러운 표현을 쓴다.
     */
    @Test
    void breakfastAndEquipmentKeepTheirOwnWordingInEveryLanguage() {
        Map<SupportedLanguage, List<String>> expected = new LinkedHashMap<>();
        //                          조식 포함 / 조식 불포함 / 장비 포함 / 장비 미포함
        expected.put(SupportedLanguage.KOREAN,
                List.of("포함", "불포함", "포함", "미포함"));
        expected.put(SupportedLanguage.ENGLISH,
                List.of("Included", "Not included", "Included", "Not included"));
        expected.put(SupportedLanguage.JAPANESE,
                List.of("込み", "込みでない", "含む", "含まない"));
        expected.put(SupportedLanguage.CHINESE_SIMPLIFIED,
                List.of("含早餐", "不含早餐", "包含", "不包含"));
        expected.put(SupportedLanguage.CHINESE_TRADITIONAL,
                List.of("含早餐", "不含早餐", "包含", "不包含"));

        for (Map.Entry<SupportedLanguage, List<String>> entry : expected.entrySet()) {
            SupportedLanguage language = entry.getKey();
            List<String> words = entry.getValue();

            assertThat(renderBoolean(language, true, BREAKFAST_INCLUDED, BREAKFAST_NOT_INCLUDED))
                    .as("%s breakfast included", language).isEqualTo(words.get(0));
            assertThat(renderBoolean(language, false, BREAKFAST_INCLUDED, BREAKFAST_NOT_INCLUDED))
                    .as("%s breakfast not included", language).isEqualTo(words.get(1));
            assertThat(renderBoolean(language, true, EQUIPMENT_INCLUDED, EQUIPMENT_NOT_INCLUDED))
                    .as("%s equipment included", language).isEqualTo(words.get(2));
            assertThat(renderBoolean(language, false, EQUIPMENT_INCLUDED, EQUIPMENT_NOT_INCLUDED))
                    .as("%s equipment not included", language).isEqualTo(words.get(3));
        }
        // 두 문맥이 같은 키를 다시 공유하지 않는다 (한국어 문구가 도로 합쳐지는 것을 막는다)
        assertThat(renderBoolean(SupportedLanguage.KOREAN, false,
                BREAKFAST_INCLUDED, BREAKFAST_NOT_INCLUDED))
                .isNotEqualTo(renderBoolean(SupportedLanguage.KOREAN, false,
                        EQUIPMENT_INCLUDED, EQUIPMENT_NOT_INCLUDED));
    }

    @Test
    void theDetailPageUsesThoseKeysForEverySubtypeBoolean() throws IOException {
        String detail = resource(DETAIL);

        // 유형별 여부 값이 쓰는 키. 값 자체(DB 텍스트)는 여기 없다.
        Map<String, String> booleanFields = new LinkedHashMap<>();
        booleanFields.put("attractionInfo.parkingAvailable", AVAILABLE);
        booleanFields.put("accommodationInfo.breakfastIncluded", BREAKFAST_INCLUDED);
        booleanFields.put("accommodationInfo.parkingAvailable", AVAILABLE);
        booleanFields.put("accommodationInfo.petAllowed", AVAILABLE);
        booleanFields.put("restaurantInfo.parkingAvailable", AVAILABLE);
        booleanFields.put("restaurantInfo.petAllowed", AVAILABLE);
        booleanFields.put("restaurantInfo.takeoutAvailable", AVAILABLE);
        booleanFields.put("restaurantInfo.deliveryAvailable", AVAILABLE);
        booleanFields.put("restaurantInfo.reservation", AVAILABLE);
        booleanFields.put("activityInfo.reservation", REQUIRED);
        booleanFields.put("activityInfo.equipmentIncluded", EQUIPMENT_INCLUDED);
        booleanFields.put("activityInfo.parkingAvailable", AVAILABLE);
        booleanFields.put("shopInfo.parkingAvailable", AVAILABLE);

        for (Map.Entry<String, String> field : booleanFields.entrySet()) {
            String block = valueBlockOf(detail, field.getKey());
            assertThat(block).as("%s reads the bundle", field.getKey())
                    .contains("#{" + field.getValue() + "}");
            // 값이 없을 때 '-' 를 쓰는 기존 동작은 그대로다
            assertThat(block).as("%s keeps its null branch", field.getKey())
                    .contains(field.getKey() + " == null");
        }
    }

    /** 해당 여부 값을 그리는 &lt;dd&gt; 한 칸만 떼어 낸다. */
    private String valueBlockOf(String detail, String field) {
        List<String> blocks = List.of(detail.split("<dd")).stream()
                .filter(block -> block.contains(field))
                .toList();
        assertThat(blocks).as("value block of %s", field).hasSize(1);
        return blocks.get(0);
    }

    @Test
    void noFixedKoreanTextIsLeftInsideTheDetailTemplate() throws IOException {
        String detail = resource(DETAIL);

        // 표현식 안의 한국어 리터럴('가능', '포함' …)은 하나도 남지 않았다
        assertThat(detail.replaceAll("(?s)<!--.*?-->", ""))
                .doesNotContainPattern("'[^'\\n]*[\\uAC00-\\uD7A3][^'\\n]*'");
    }

    @Test
    void everyBundleDefinesTheSameFixedValueKeys() throws IOException {
        List<String> keys = List.of(AVAILABLE, UNAVAILABLE, REQUIRED, NOT_REQUIRED,
                BREAKFAST_INCLUDED, BREAKFAST_NOT_INCLUDED,
                EQUIPMENT_INCLUDED, EQUIPMENT_NOT_INCLUDED);
        for (String bundle : new String[]{"/messages.properties", "/messages_ko.properties",
                "/messages_en.properties", "/messages_ja.properties",
                "/messages_zh_CN.properties", "/messages_zh_TW.properties"}) {
            String content = resource(bundle);
            for (String key : keys) {
                assertThat(content).as("%s in %s", key, bundle).contains(key + "=");
            }
            // 합쳐 쓰던 옛 키는 남아 있지 않다
            assertThat(content).as("old shared key in %s", bundle)
                    .doesNotContain("destination.detail.value.included")
                    .doesNotContain("destination.detail.value.notIncluded");
        }
    }

    /** detail.html 이 쓰는 것과 같은 조건식을 그대로 렌더링한다. */
    private String renderBoolean(SupportedLanguage language, boolean value,
                                 String trueKey, String falseKey) {
        Context context = new Context(language.getLocale());
        context.setVariable("flag", value);
        String template = "<span th:text=\"${flag}"
                + " ? #{" + trueKey + "}"
                + " : #{" + falseKey + "}\">x</span>";
        String html = engine.process(template, context);
        return html.replaceAll("<[^>]+>", "").trim();
    }

    private SpringTemplateEngine newEngine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        // 애플리케이션과 같이 시스템 로케일로 새지 않게 한다
        messageSource.setFallbackToSystemLocale(false);

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        templateEngine.setTemplateEngineMessageSource(messageSource);
        return templateEngine;
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
