package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdminInfoCategoryUiContractTest {

    @Test
    void listUsesPostDeleteWithConfirmation() throws IOException {
        String list = resource("/templates/admin/info-categories/list.html");

        assertThat(list)
                .contains("th:action=\"@{/admin/info-categories/{id}/delete(id=${category.id})}\"")
                .contains("method=\"post\"")
                .contains("confirm('이 정보 카테고리를 삭제하시겠습니까?')")
                .contains("class=\"admin-btn is-small is-danger\">삭제</button>");
    }

    @Test
    void dashboardEnablesInfoCategoryAndKeepsTravelInfoDisabled() throws IOException {
        String dashboard = resource("/templates/admin/index.html");

        assertThat(dashboard)
                .contains("<a class=\"admin-dashboard-link\" th:href=\"@{/admin/info-categories}\">정보 카테고리</a>")
                .contains("여행정보 <small>준비 중</small>");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
