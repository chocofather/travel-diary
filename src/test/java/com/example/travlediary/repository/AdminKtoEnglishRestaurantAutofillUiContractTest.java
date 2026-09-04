package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 영문 TourAPI 상세 자동입력이 식당 영문 번역칸으로 이어지는 화면 규약.
 *
 * <p>대상은 영어 슬롯의 대표메뉴·영업시간·휴무일 세 칸뿐이고,
 * 값이 비면 기존 입력을 덮지 않는다.
 */
class AdminKtoEnglishRestaurantAutofillUiContractTest {

    @Test
    void everyLanguageSlotCarriesTheThreeRestaurantAutofillHooks() throws IOException {
        String all = resource("/templates/admin/destinations/fragments/translation-tabs.html");
        // 식당 조각만 떼어 본다 (여행지 기본정보 조각도 같은 파일에 있다)
        String fragment = all.substring(all.indexOf("th:fragment=\"restaurantTranslations"));

        for (String field : new String[]{"mainMenu", "openingHours", "closedDays"}) {
            assertThat(fragment).as("hook for %s", field)
                    .contains("data-kto-tour-foreign-field=${ktoAutofill} ? '" + field + "' : null");
        }
        // 자동입력은 등록 화면(ktoAutofill=true)에서만 붙고, 언어는 슬롯 코드로 정해진다
        assertThat(count(fragment, "data-kto-tour-foreign-field=")).isEqualTo(3);
        assertThat(count(fragment,
                "data-kto-tour-foreign-language=\n"
                        + "                            ${ktoAutofill} ? ${translation.languageCode} : null"))
                .isEqualTo(3);
        // 번역 대상에서 제외한 값에는 훅을 달지 않는다
        for (String field : new String[]{"priceRange", "breakTime", "etc", "contactNumber"}) {
            assertThat(fragment).as("no hook for %s", field)
                    .doesNotContain("? '" + field + "' : null");
        }
        // 언어를 화면에 박지 않는다 (영어 전용 훅은 더 이상 없다)
        assertThat(fragment)
                .doesNotContain("data-kto-tour-english-field")
                .doesNotContain("translation.languageCode == 'en'");
    }

    /** 유형마다 자동입력 대상 칸이 다르다. 대응이 분명하지 않은 칸에는 훅을 달지 않는다. */
    @Test
    void eachSubtypeFragmentHooksOnlyItsMappedFields() throws IOException {
        String all = resource("/templates/admin/destinations/fragments/translation-tabs.html");

        // 파일에 놓인 순서대로 다음 조각 전까지를 본다
        assertHooks(all, "accommodationTranslations", "attractionTranslations",
                new String[]{"roomType"},
                new String[]{"etc"});
        assertHooks(all, "attractionTranslations", "activityTranslations",
                new String[]{"closedDays", "openingHours", "admissionFee"},
                new String[]{"guide"});
        assertHooks(all, "activityTranslations", "shopTranslations",
                new String[]{"openingHours", "admissionFee"},
                new String[]{"requiredTime", "ageLimit", "guide"});
        assertHooks(all, "shopTranslations", "restaurantTranslations",
                new String[]{"closedDays", "openingHours", "mainProducts"},
                new String[]{"guide"});
    }

    private void assertHooks(String all, String fragmentName, String nextFragmentName,
                             String[] hooked, String[] notHooked) {
        String fragment = between(all, "th:fragment=\"" + fragmentName,
                "th:fragment=\"" + nextFragmentName);
        for (String field : hooked) {
            assertThat(fragment).as("%s hooks %s", fragmentName, field)
                    .contains("data-kto-tour-foreign-field=${ktoAutofill} ? '" + field + "' : null");
        }
        for (String field : notHooked) {
            assertThat(fragment).as("%s must not hook %s", fragmentName, field)
                    .doesNotContain("'" + field + "' : null");
        }
        assertThat(count(fragment, "data-kto-tour-foreign-field="))
                .as("%s hook count", fragmentName).isEqualTo(hooked.length);
    }

    @Test
    void theScriptSendsTheMatchedContentTypeIdAndFillsOnlyEmptyFields() throws IOException {
        String script = resource("/static/js/admin-kto-tour-autofill.js");

        assertThat(script)
                // 매칭으로 받은 외국어 유형 코드를 그대로 넘긴다 (하드코딩 없음)
                .contains("detailParams.set(\"contentTypeId\", matched.matched.contentTypeId)")
                .doesNotContain("contentTypeId: \"82\"")
                .doesNotContain("contentTypeId=82")
                // 언어별 title/overview 자동입력
                .contains("fillIfEmpty(nameInputForLanguage, localizedTitle)")
                .contains("fillIfEmpty(overviewInputForLanguage, detail.overview)")
                // 유형별 칸은 언어 코드로 찾는다 (슬롯 번호를 박지 않는다)
                .contains("const SUBTYPE_FIELDS = [")
                .contains("\"closedDays\", \"openingHours\", \"admissionFee\", "
                        + "\"mainMenu\", \"roomType\", \"mainProducts\"")
                .contains("data-kto-tour-foreign-field=\"${fieldName}\"")
                .contains("data-kto-tour-foreign-language=\"${languageCode}\"")
                .doesNotContain("translations[");
        // 번역 대상에서 제외한 값은 외국어 자동입력이 건드리지 않는다
        // (국문 자동입력의 연락처 등과 섞이지 않도록 해당 함수 안만 본다)
        assertThat(between(script, "async function fillForeignLanguage", "function sameAsKoreanTitle"))
                .doesNotContain("priceRange", "breakTime", "contactNumber", "shortDescription",
                        "requiredTime", "ageLimit", "guide");
        // 빈 값/이미 입력된 값은 건드리지 않는 기존 규칙을 그대로 쓴다
        assertThat(between(script, "function fillSubtypeField", "function fillIfEmpty"))
                .contains("element.value.trim()")
                .contains("fillIfEmpty(");
        assertThat(between(script, "function fillIfEmpty", "setFieldValue"))
                .contains("if (!element || element.value.trim()) return;")
                .contains("String(value).trim() === \"\"");
    }

    private int count(String source, String token) {
        return source.split(Pattern.quote(token), -1).length - 1;
    }

    private String between(String source, String start, String end) {
        int from = source.indexOf(start);
        assertThat(from).as("start %s", start).isNotNegative();
        int to = source.indexOf(end, from);
        assertThat(to).as("end %s", end).isNotNegative();
        return source.substring(from, to);
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
