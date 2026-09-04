package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 여행지 등록/수정 화면의 식당 번역 입력 슬롯.
 *
 * <p>한국어는 기존 입력을 그대로 쓰고, 나머지 네 언어만 슬롯으로 받는다.
 * 화면 문구는 계속 한국어다.
 */
class AdminRestaurantTranslationFormUiContractTest {

    private static final String[] ADMIN_FORMS = {
            "/templates/admin/destinations/create.html",
            "/templates/admin/destinations/edit.html"
    };

    private static final String TRANSLATION_FRAGMENT =
            "/templates/admin/destinations/fragments/translation-tabs.html";

    @Test
    void bothFormsReuseTheSharedTranslationFragment() throws IOException {
        for (String path : ADMIN_FORMS) {
            assertThat(resource(path)).as(path)
                    .contains("admin/destinations/fragments/translation-tabs")
                    .contains(":: restaurantTranslations(");
        }
        // 등록 화면만 TourAPI 자동입력을 쓴다
        assertThat(resource(ADMIN_FORMS[0])).contains(":: restaurantTranslations(true)");
        assertThat(resource(ADMIN_FORMS[1])).contains(":: restaurantTranslations(false)");
    }

    @Test
    void theFragmentBindsTheFourTranslationSlotsWithKoreanLabels() throws IOException {
        String fragment = resource(TRANSLATION_FRAGMENT);

        assertThat(fragment)
                .contains("th:each=\"translation, slot : *{restaurantInfoTranslations}\"")
                .contains("restaurantInfoTranslations[__${slot.index}__].languageCode")
                .contains("restaurantInfoTranslations[__${slot.index}__].mainMenu")
                .contains("restaurantInfoTranslations[__${slot.index}__].priceRange")
                .contains("restaurantInfoTranslations[__${slot.index}__].openingHours")
                .contains("restaurantInfoTranslations[__${slot.index}__].breakTime")
                .contains("restaurantInfoTranslations[__${slot.index}__].closedDays")
                .contains("restaurantInfoTranslations[__${slot.index}__].etc")
                // 언어 이름은 서버가 준 라벨을 쓴다 (탭은 원어, 보조 설명은 한국어)
                .contains("${restaurantTranslationLabels.get(translation.languageCode)}")
                .contains("${translationTabLabels.get(translation.languageCode)}")
                .contains("음식점/카페 상세 정보 번역")
                .contains("위에 입력한 값이 한국어로 함께 저장됩니다.");
    }

    @Test
    void koreanAndNonTranslatableFieldsKeepTheirOriginalBinding() throws IOException {
        String fragment = resource(TRANSLATION_FRAGMENT);
        for (String path : ADMIN_FORMS) {
            String template = resource(path);

            // 한국어(원본) 자유 텍스트 입력은 화면에 그대로 하나씩만 있다
            for (String field : new String[]{
                    "mainMenu", "priceRange", "openingHours", "breakTime", "closedDays", "etc"}) {
                assertThat(count(template, "*{restaurantInfo." + field + "}"))
                        .as("%s base binding in %s", field, path)
                        .isEqualTo(1);
            }
            // 번역하지 않는 값도 예전 그대로 하나씩이다
            for (String field : new String[]{
                    "parkingAvailable", "petAllowed", "seatCount", "takeoutAvailable",
                    "deliveryAvailable", "reservation", "contactNumber", "homepageUrl"}) {
                assertThat(count(template, "*{restaurantInfo." + field + "}"))
                        .as("%s binding in %s", field, path)
                        .isEqualTo(1);
                assertThat(fragment).as("%s is not translated", field)
                        .doesNotContain("restaurantInfoTranslations[__${slot.index}__]." + field);
            }
        }
    }

    private int count(String source, String token) {
        return source.split(Pattern.quote(token), -1).length - 1;
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
