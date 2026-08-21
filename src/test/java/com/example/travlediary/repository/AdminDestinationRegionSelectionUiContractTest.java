package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdminDestinationRegionSelectionUiContractTest {

    private static final String REGION_SELECTOR_ASSET = "/js/region-selector.js?v=20260821-3";

    @Test
    void editFormPassesTheStoredRegionPathAndRegionErrorToTheSelector() throws IOException {
        String edit = resource("/templates/admin/destinations/edit.html");

        assertThat(edit)
                .contains("id=\"regionIdHidden\"")
                .contains("th:field=\"*{regionId}\"")
                .contains("data-initial-region-path=${regionPathIds}")
                .contains("th:errors=\"*{regionId}\"")
                .contains(REGION_SELECTOR_ASSET);
    }

    @Test
    void createFormKeepsTheSameSelectorAssetVersion() throws IOException {
        String create = resource("/templates/admin/destinations/create.html");

        assertThat(create).contains(REGION_SELECTOR_ASSET);
    }

    @Test
    void selectorRestoresTheInitialPathAndKeepsTheStoredRegionUntilItIsChanged() throws IOException {
        String script = resource("/static/js/region-selector.js");

        assertThat(script)
                .contains("regionIdHidden.dataset.initialRegionPath")
                .contains("const initialRegionId = regionIdHidden.value")
                .contains("let regionSelectionChanged = false")
                .contains("regionIdHidden.value = selectedId || (regionSelectionChanged ? \"\" : initialRegionId)")
                .contains("if (initialRegionPath.length > 0)")
                .doesNotContain("regionIdHidden.value = \"\";");
    }

    @Test
    void selectorStillSupportsManualSelectionAndTourApiRegionApply() throws IOException {
        String script = resource("/static/js/region-selector.js");

        assertThat(script)
                .contains("window.TravelDiaryRegionSelector")
                .contains("applyRegionPath")
                .contains("clearSelection")
                .contains("handleManualChange")
                .contains("form.addEventListener(\"submit\", updateRegionId)");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
