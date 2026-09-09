package com.example.travlediary.repository;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CourseTimelineUiContractTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void templateKeepsStopDomOrderAndAllExistingCardInformation() throws IOException {
        String template = resource("templates/course/detail.html");

        assertThat(template)
                .contains("th:each=\"stop : ${course.stops}\"")
                .contains("class=\"course-stop\"")
                .contains("th:text=\"${stop.visitOrder}\"")
                .doesNotContain("class=\"course-stop-order\"")
                .contains("@{/destinations/{id}(id=${stop.destinationId})}")
                .contains("stop.imageUrl")
                .contains("'/images/default.png'")
                .contains("th:text=\"${stop.name}\"")
                .contains("stop.shortDescription")
                .contains("th:text=\"${stop.regionName}\"")
                .doesNotContain("stopStat")
                .doesNotContain("course-stop--left")
                .doesNotContain("course-stop--right")
                .doesNotContain("reverse(");
    }

    @Test
    void ownerActionsKeepTheirPermissionAndDeleteContractInTheHeader() throws IOException {
        var document = Jsoup.parse(resource("templates/course/detail.html"));
        var menu = document.selectFirst(".course-summary-header .course-owner-menu");

        assertThat(menu).isNotNull();
        assertThat(menu.attr("th:if")).isEqualTo("${course.myCourse}");
        assertThat(menu.selectFirst("summary")).isNotNull();
        assertThat(menu.selectFirst("a.course-edit-button").attr("th:href"))
                .isEqualTo("@{/course/{id}/edit(id=${course.id})}");
        var deleteForm = menu.selectFirst("form.course-delete-form");
        assertThat(deleteForm.attr("method")).isEqualTo("post");
        assertThat(deleteForm.attr("th:action"))
                .isEqualTo("@{/course/{id}/delete(id=${course.id})}");
        assertThat(deleteForm.attr("th:data-confirm")).isEqualTo("#{course.detail.deleteConfirm}");
        assertThat(document.select(".course-detail-footer .course-edit-button, "
                + ".course-detail-footer .course-delete-form")).isEmpty();
        assertThat(document.select(".course-summary-label .course-stop-count")).hasSize(1);
    }

    @Test
    void desktopUsesThreeColumnSixStopSnakeWithoutCssOrder() throws IOException {
        String css = resource("static/css/course-detail.css");

        assertThat(css)
                .contains("grid-template-columns: repeat(3, minmax(0, 1fr))")
                .contains("grid-auto-flow: row dense")
                .contains(".course-stop:nth-of-type(6n + 1)")
                .contains(".course-stop:nth-of-type(6n + 2)")
                .contains(".course-stop:nth-of-type(6n + 3)")
                .contains(".course-stop:nth-of-type(6n + 4)")
                .contains(".course-stop:nth-of-type(6n + 5)")
                .contains(".course-stop:nth-of-type(6n)")
                .contains("grid-column: 1")
                .contains("grid-column: 2")
                .contains("grid-column: 3")
                .doesNotContain("flex-direction: row-reverse")
                .doesNotContain("\n    order:");
    }

    @Test
    void connectorsStopAtLastCardAndTurnAtEachRowEnd() throws IOException {
        String css = resource("static/css/course-detail.css");

        assertThat(css)
                .contains(".course-stop::before")
                .contains(".course-stop::after")
                .contains(":not(:last-child)::before")
                .contains(".course-stop:nth-of-type(6n + 3):not(:last-child)::after")
                .contains(".course-stop:nth-of-type(6n):not(:last-child)::after")
                .contains("--route-line-color: #b8d6c9")
                .contains("height: calc(100% + var(--route-row-gap))")
                .contains("z-index: 2")
                .contains("align-items: stretch");
    }

    @Test
    void mobileFullyResetsSnakeAndProvidesResponsiveCardSizes() throws IOException {
        String css = resource("static/css/course-detail.css");
        int mobileStart = css.indexOf("@media (max-width: 900px)");
        String mobile = css.substring(mobileStart);

        assertThat(mobileStart).isNotNegative();
        assertThat(mobile)
                .contains("display: flex")
                .contains("flex-direction: column")
                .contains(".course-stop:nth-of-type(n)")
                .contains("grid-column: auto")
                .contains(".course-timeline > .course-stop:nth-of-type(n):not(:last-child)::before")
                .contains("height: calc(100% + var(--route-row-gap))")
                .doesNotContain("linear-gradient")
                .contains("@media (max-width: 480px)")
                .contains("@media (max-width: 320px)")
                .contains("minmax(0, 1fr)");
    }

    private String resource(String relativePath) throws IOException {
        return Files.readString(RESOURCES.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
