package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTravelInfoUiContractTest {

    @Test
    void menuAndDashboardLinkToTravelInfo() throws IOException {
        assertThat(resource("/templates/fragments/admin/sidebar.html"))
                .contains("th:href=\"@{/admin/travel-info}\">여행정보</a>")
                .contains("activeMenu == 'travel-info'")
                .doesNotContain("여행정보</span>\n        <span class=\"admin-nav-badge\">준비 중");
        assertThat(resource("/templates/admin/index.html"))
                .contains("th:href=\"@{/admin/travel-info}\">여행정보</a>");
    }

    @Test
    void listUsesGetFiltersAndPostDeleteWithoutImageFeatures() throws IOException {
        String list = resource("/templates/admin/travel-info/list.html");

        assertThat(list)
                .contains("th:action=\"@{/admin/travel-info}\" method=\"get\"")
                .contains("name=\"scope\"")
                .contains("name=\"contentType\"")
                .contains("name=\"categoryId\"")
                .contains("th:action=\"@{/admin/travel-info/{id}/delete(id=${info.id})}\"")
                .contains("method=\"post\"")
                .doesNotContain("info_images", "image-upload", "대표 이미지");
    }

    @Test
    void formUsesSingleFormToastEditorAndIndexedPeriods() throws IOException {
        String form = resource("/templates/admin/travel-info/form.html");

        assertThat(form)
                .containsOnlyOnce("<form id=\"travel-info-form\"")
                .contains("id=\"travel-info-editor\"")
                .contains("th:field=\"*{content}\"")
                .contains("th:field=\"*{periods[__${periodStat.index}__].startDate}\"")
                .contains("th:field=\"*{periods[__${periodStat.index}__].endDate}\"")
                .contains("/js/editor-init.js")
                .contains("/js/admin-travel-info-form.js")
                .doesNotContain("multipart/form-data", "info_images", "mainIdx", "orderIndex");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
