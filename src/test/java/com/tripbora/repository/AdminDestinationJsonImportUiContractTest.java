package com.tripbora.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JSON 일괄 등록 화면 스크립트 계약. 이 PC 에는 node 가 없어 .cjs 테스트를 돌릴 수 없으므로 핵심 동작을 문자열로 고정한다.
 */
class AdminDestinationJsonImportUiContractTest {

    @Test
    void registrationSendsTheOriginalItemOneByOneWithThePerRowApproval() throws IOException {
        String script = resource("/static/js/admin-destination-json-import.js");

        assertThat(script)
                // 미리보기와 등록은 서버가 검증한다.
                .contains("/admin/api/destinations/import-json/preview")
                .contains("/admin/api/destinations/import-json/register")
                // 미리보기 때의 원본 항목을 그대로 보낸다(서버가 다시 검증한다).
                .contains("item: documentSnapshot.destinations[row.preview.index]")
                // 중복 확인 행은 행별 승인했을 때만 넘긴다.
                .contains("allowPossibleDuplicate: row.preview.status === 'POSSIBLE_DUPLICATE' && row.approved")
                .contains("다른 여행지임을 확인")
                // 한 건씩 차례로 보낸다.
                .contains("for (const row of targets)")
                .contains("await postJson('/admin/api/destinations/import-json/register'")
                // 미리보기 뒤 JSON 을 고치면 다시 미리보기 전까지 등록할 수 없다.
                .contains("textArea.value !== snapshotText")
                // 상태를 바꾸는 요청에는 CSRF 토큰을 싣는다.
                .contains("meta[name=\"_csrf_header\"]");
    }

    @Test
    void registeredAndInvalidRowsCannotBeSelectedAndPossibleDuplicatesNeedApproval() throws IOException {
        String script = resource("/static/js/admin-destination-json-import.js");

        assertThat(script)
                .contains("if (row.preview.status === 'NOT_REGISTERED') return true;")
                .contains("if (row.preview.status === 'POSSIBLE_DUPLICATE') return row.approved;")
                .contains("['SUCCESS', 'REGISTERED', 'RUNNING'].includes(row.result.status)")
                .contains("/admin/destinations/edit/")
                // 성공 행 링크는 관리자 수정폼으로 가므로 그 목적이 드러나는 문구를 쓴다.
                .contains("existingLink(row.result.destinationId, '등록한 여행지 수정')")
                .doesNotContain("새 여행지")
                .contains("MAX_BYTES = 1048576");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
