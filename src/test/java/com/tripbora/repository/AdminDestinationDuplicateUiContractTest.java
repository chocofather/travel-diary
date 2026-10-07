package com.tripbora.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 등록폼의 두 단건 검색(TourAPI·Wikidata)이 공통 중복 판별 결과를 같은 방식으로 보여주고,
 * '중복 확인' 후보를 확인하고 고른 경우에만 저장폼의 확인 칸을 채우는지 고정한다.
 */
class AdminDestinationDuplicateUiContractTest {

    @Test
    void bothSingleSearchesShowTheSameBadgesLinksAndRegisteredLock() throws IOException {
        for (String path : new String[]{"/static/js/admin-wikidata-preview.js", "/static/js/admin-kto-tour-autofill.js"}) {
            String script = resource(path);
            assertThat(script).as(path)
                    // 서버 판별 결과를 그대로 쓴다.
                    .contains("REGISTERED", "POSSIBLE_DUPLICATE")
                    // 같은 배지 클래스와 문구
                    .contains("admin-kto-tour-badge", "등록됨", "중복 확인")
                    // 기존 여행지 관리 링크
                    .contains("admin-kto-tour-existing", "/admin/destinations/edit/")
                    // 등록됨은 선택 버튼을 잠근다.
                    .contains("disabled = registered")
                    // 중복 확인은 confirm 을 거친다.
                    .contains("window.confirm(")
                    // 확인 결과는 저장폼 확인 칸에 이벤트로 알린다.
                    .contains("tripbora:possible-duplicate");
        }
    }

    @Test
    void theWikidataSingleSearchReadsTheDuplicateFromSearchDetails() throws IOException {
        String script = resource("/static/js/admin-wikidata-preview.js");

        assertThat(script)
                .contains("/search-details?")
                .contains("candidate.duplicate")
                // 후보를 실제로 바꿀 때만 확인 칸을 그 후보 기준으로 다시 정한다.
                .contains("acknowledgedDuplicates.get(qid)");
    }

    @Test
    void theAcknowledgementListenerChecksTheSaveFormBox() throws IOException {
        String script = resource("/static/js/admin-destination-duplicate-ack.js");

        assertThat(script)
                .contains("addEventListener('tripbora:possible-duplicate'")
                .contains("[data-possible-duplicate-ack]")
                .contains("[data-allow-possible-duplicate]")
                .contains("input.checked = Boolean(duplicate)");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
