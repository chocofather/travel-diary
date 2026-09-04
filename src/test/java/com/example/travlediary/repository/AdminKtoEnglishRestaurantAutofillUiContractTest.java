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
    void onlyTheEnglishSlotCarriesTheThreeAutofillHooks() throws IOException {
        String fragment = resource("/templates/admin/destinations/fragments/translation-tabs.html");

        for (String field : new String[]{"mainMenu", "openingHours", "closedDays"}) {
            assertThat(fragment).as("english hook for %s", field)
                    .contains("? '" + field + "' : null");
        }
        // 자동입력은 등록 화면(ktoAutofill=true)의 영어 슬롯에서만 붙는다
        assertThat(count(fragment, "${ktoAutofill and translation.languageCode == 'en'}"))
                .isEqualTo(3);
        // 번역 대상에서 제외한 값에는 훅을 달지 않는다
        for (String field : new String[]{"priceRange", "breakTime", "etc", "contactNumber"}) {
            assertThat(fragment).as("no english hook for %s", field)
                    .doesNotContain("? '" + field + "' : null");
        }
        assertThat(count(fragment, "data-kto-tour-english-field=")).isEqualTo(3);
    }

    @Test
    void theScriptSendsTheMatchedContentTypeIdAndFillsOnlyEmptyFields() throws IOException {
        String script = resource("/static/js/admin-kto-tour-autofill.js");

        assertThat(script)
                // 매칭으로 받은 영문 유형 코드를 그대로 넘긴다 (하드코딩 없음)
                .contains("params.set(\"contentTypeId\", candidate.contentTypeId)")
                .doesNotContain("contentTypeId: \"82\"")
                .doesNotContain("contentTypeId=82")
                // 기존 title/overview 자동입력은 그대로다
                .contains("fillIfEmpty(englishNameInput, payload.title)")
                .contains("fillIfEmpty(englishOverviewInput, payload.overview)")
                // 식당 세 칸만 추가로 채운다
                .contains("fillEnglishField(\"mainMenu\", payload.mainMenu)")
                .contains("fillEnglishField(\"openingHours\", payload.openingHours)")
                .contains("fillEnglishField(\"closedDays\", payload.closedDays)")
                .doesNotContain("payload.priceRange")
                .doesNotContain("payload.breakTime")
                .doesNotContain("payload.contactNumber");
        // 빈 값/이미 입력된 값은 건드리지 않는 기존 규칙을 그대로 쓴다
        assertThat(between(script, "function fillEnglishField", "}"))
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
