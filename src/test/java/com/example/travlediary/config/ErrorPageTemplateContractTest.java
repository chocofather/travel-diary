package com.example.travlediary.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorPageTemplateContractTest {

    private static final Path ERROR_TEMPLATE_DIRECTORY =
            Path.of("src/main/resources/templates/error");

    @Test
    void everyHttpErrorStatusAndSeriesFallbackUsesTheSharedSafeErrorView() throws IOException {
        Map<String, String> templates = Map.of(
                "400", "error.400.title",
                "403", "error.403.title",
                "404", "error.404.title",
                "405", "error.405.title",
                "429", "error.429.title",
                "500", "error.500.title",
                "503", "error.503.title",
                "4xx", "error.4xx.title",
                "5xx", "error.5xx.title");

        for (Map.Entry<String, String> template : templates.entrySet()) {
            String source = Files.readString(ERROR_TEMPLATE_DIRECTORY.resolve(template.getKey() + ".html"));

            assertThat(source)
                    .contains("error/error-page :: errorPage")
                    .contains(template.getValue());
        }
    }

    @Test
    void sharedErrorViewOffersSafeNavigationWithoutRenderingInternalErrorAttributes()
            throws IOException {
        String source = Files.readString(ERROR_TEMPLATE_DIRECTORY.resolve("error-page.html"));

        assertThat(source)
                .contains("Tripbora")
                .contains("th:href=\"@{/}\"")
                .contains("error.home")
                .doesNotContain("${exception}")
                .doesNotContain("${trace}")
                .doesNotContain("${message}")
                .doesNotContain("${path}");
    }
}
