package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 여행지 등록/수정 폼 계약.
 * - 영문 입력 영역의 관리자 문구는 한국어로 통일한다.
 * - 지역 선택은 국내/해외를 먼저 고르고 그 계층만 노출한다.
 */
class AdminDestinationRegionModeUiContractTest {

    @Test
    void englishSectionUsesKoreanAdminLabelsWhileKeepingTheEnglishBinding() throws IOException {
        String create = resource("/templates/admin/destinations/create.html");

        // 관리자 UI 문구는 한국어
        assertThat(create)
                .contains("영문 정보")
                .contains("여행지명 (영문)")
                .contains("간단 설명 (영문)")
                .contains("상세 설명 (영문)")
                .doesNotContain(">English<")
                .doesNotContain(">Destination name")
                .doesNotContain(">Short description")
                .doesNotContain(">Description");

        // 영문 저장 계약(en slot, 바인딩, TourAPI 자동입력 훅)은 그대로
        assertThat(create)
                .contains("*{translations[1].languageCode}")
                .contains("*{translations[1].name}")
                .contains("*{translations[1].shortDescription}")
                .contains("*{translations[1].description}")
                .contains("data-kto-tour-english-name")
                .contains("data-kto-tour-english-overview");
    }

    @Test
    void regionPickerStartsWithADomesticOrOverseasChoice() throws IOException {
        for (String path : new String[]{
                "/templates/admin/destinations/create.html",
                "/templates/admin/destinations/edit.html"}) {
            String form = resource(path);

            assertThat(form).as("form %s", path)
                    // 국내/해외 toggle (키보드 접근 가능한 button + 상태 표시)
                    .contains("data-region-mode-button=\"domestic\"")
                    .contains("data-region-mode-button=\"overseas\"")
                    .contains("aria-pressed")
                    .contains("data-region-mode")
                    // 국내/해외가 섞이던 첫 라벨은 사라진다
                    .doesNotContain("대륙 / 국내")
                    .doesNotContain("국가 / 시·도")
                    .doesNotContain("도시 / 시·군·구")
                    // 단계별 라벨은 모드에 따라 달라진다
                    .contains("data-region-step")
                    // 저장 계약은 그대로 (가장 깊은 내부 regionId 하나)
                    .contains("id=\"regionIdHidden\"")
                    .contains("th:field=\"*{regionId}\"")
                    // 국내 root 는 서버가 내려준 값으로만 판별한다 (숫자 하드코딩 금지)
                    .contains("data-domestic-root-id=${domesticRootId}");
        }
    }

    @Test
    void selectorSwitchesModeWithoutKeepingTheOtherModesRegion() throws IOException {
        String script = resource("/static/js/region-selector.js");

        assertThat(script)
                // 서버가 내려준 국내 root id 로만 국내/해외를 구분한다
                .contains("domesticRootId")
                .doesNotContain("=== 7")
                .doesNotContain("== 7")
                // 모드 전환 시 이전 모드의 선택과 hidden regionId 를 비운다
                .contains("data-region-mode-button")
                .contains("aria-pressed")
                .contains("resetSelect")
                .contains("updateRegionId")
                // 기존 계약 유지: 부모 변경 시 하위 초기화, TourAPI applyRegionPath, edit 복원
                .contains("clearAfter")
                .contains("applyRegionPath")
                .contains("initialRegionPath")
                .contains("regionSelectionChanged")
                // 경로를 복원하면 국내/해외 모드도 함께 복원된다
                .contains("domestic")
                .contains("overseas");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
