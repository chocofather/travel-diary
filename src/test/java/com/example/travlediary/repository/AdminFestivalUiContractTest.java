package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AdminFestivalUiContractTest {

    @Test
    void festivalListUsesSeparateRouteAndFestivalColumns() throws IOException {
        String list = resource("/templates/admin/festivals/list.html");

        assertThat(list)
                .contains("layout(~{::body}, ~{::headFragment}, 'festivals')")
                .contains("th:action=\"@{/admin/festivals}\" method=\"get\"")
                .contains("name=\"scope\"", "name=\"categoryId\"")
                .contains("축제 분류", "행사기간")
                .contains("#temporals.format(festival.startDate, 'yyyy-MM-dd')")
                .contains("#temporals.format(festival.endDate, 'yyyy-MM-dd')")
                .contains("@{/admin/festivals/create}")
                .contains("@{/admin/festivals/{id}(id=${festival.id})}")
                .contains("@{/admin/festivals/edit/{id}(id=${festival.id})}")
                .contains("aria-disabled=\"true\"")
                .doesNotContain("method=\"post\"", "travel-info/create", "data-kto-festival-autofill");
    }

    @Test
    void festivalCreateFormKeepsTourApiDetailsOutsideTheOverviewEditor() throws IOException {
        String form = resource("/templates/admin/festivals/form.html");
        String autofill = resource("/static/js/admin-festival-autofill.js");

        assertThat(form)
                .contains("layout(~{::body}, ~{::headFragment}, 'festivals')")
                .contains("/js/admin-festival-autofill.js")
                .contains("data-admin-festival-autofill")
                .contains("data-festival-search-mode=\"keyword\"", "data-festival-search-mode=\"period\"")
                .contains("data-festival-search-panel=\"keyword\"", "data-festival-search-panel=\"period\"")
                .contains("data-festival-search-keyword")
                .contains("data-festival-period-year", "data-festival-month-grid", "data-festival-month=\"1\"",
                        "data-festival-month=\"12\"", "data-festival-direct-period-toggle",
                        "data-festival-direct-period-panel")
                .contains("data-festival-search-start-date", "data-festival-search-end-date",
                        "data-festival-keyword-search-button", "data-festival-period-search-button",
                        "data-festival-results")
                .contains("id=\"festival-title\"", "id=\"festival-category\"",
                        "id=\"festival-scope\"", "id=\"festival-start-date\"", "id=\"festival-end-date\"")
                .contains("id=\"festival-editor\"", "id=\"festival-content\"")
                .contains("id=\"festival-event-place\"", "id=\"festival-address\"",
                        "id=\"festival-play-time\"", "id=\"festival-use-time\"")
                .contains("id=\"festival-sponsor1\"", "id=\"festival-sponsor1-tel\"",
                        "id=\"festival-sponsor2\"", "id=\"festival-sponsor2-tel\"")
                .contains("id=\"festival-contact-tel\"", "id=\"festival-homepage-url\"")
                .contains("th:object=\"${festivalForm}\"", "method=\"post\"",
                        "th:action=\"@{/admin/festivals/create}\"", "id=\"kto-festival-content-id\"")
                .doesNotContain("admin-travel-info-festival-autofill.js");

        assertThat(autofill)
                .contains("/admin/api/kto/festivals/search-by-keyword?${params.toString()}")
                .contains("/admin/api/kto/festivals/search?${params.toString()}")
                .contains("/admin/api/kto/festivals/detail?${params.toString()}")
                .contains("setSearchMode('keyword')")
                .contains("results.replaceChildren()")
                .contains("async function searchByKeyword()", "async function searchByPeriod()",
                        "function renderCandidates(items)", "function createCandidate(item)")
                .contains("function searchByMonth(month, button)", "function monthDateRange(year, month)",
                        "new Date(year, month - 1, 1)", "new Date(year, month, 0)")
                .contains("function toggleDirectPeriod()", "directPeriodPanel.hidden = !expanded")
                .contains("await searchByPeriodRange(range.startDate, range.endDate, button)",
                        "await searchByPeriodRange(normalizedStartDate, normalizedEndDate, periodSearchButton)")
                .contains("setManagedField('scope', scope, 'DOMESTIC')")
                .contains("setManagedField('eventPlace', eventPlace, detail.eventPlace)")
                .contains("setManagedField('address', address, detail.address)")
                .contains("setManagedField('playTime', playTime, detail.playTime)")
                .contains("setManagedField('useTime', useTime, detail.useTimeFestival)")
                .contains("setManagedField('sponsor1', sponsor1, detail.sponsor1)")
                .contains("setManagedField('sponsor1Tel', sponsor1Tel, detail.sponsor1Tel)")
                .contains("setManagedField('sponsor2', sponsor2, detail.sponsor2)")
                .contains("setManagedField('sponsor2Tel', sponsor2Tel, detail.sponsor2Tel)")
                .contains("setManagedField('contactTel', contactTel, detail.tel)")
                .contains("setManagedField('homepageUrl', homepageUrl, detail.eventHomepage || detail.homepage)")
                .contains("setManagedEditor(detail.overview)")
                .doesNotContain("buildFestivalHtml", "행사 정보");
    }

    @Test
    void festivalCreateFormUsesNullSafeScopeSelectionInsteadOfEnumEquality() throws IOException {
        String form = resource("/templates/admin/festivals/form.html");

        assertThat(form)
                .contains("th:field=\"*{scope}\"")
                .doesNotContain("scope == T(com.example.travlediary.model.TravelInfoScope).DOMESTIC",
                        "T(com.example.travlediary.model.TravelInfoScope).DOMESTIC.equals(scope)");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
