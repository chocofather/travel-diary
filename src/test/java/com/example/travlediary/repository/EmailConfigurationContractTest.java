package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EmailConfigurationContractTest {

    @Test
    void trackedConfigurationReferencesSecretsAndBaseUrlThroughEnvironmentVariables() throws IOException {
        String configuration = resource("application.yml");

        assertThat(configuration)
                .contains("username: ${MAIL_USERNAME:}")
                .contains("password: ${MAIL_PASSWORD:}")
                .contains("server-url: ${APP_BASE_URL:http://localhost:8080}")
                .contains("connectiontimeout: 5000")
                .contains("timeout: 5000")
                .contains("writetimeout: 5000")
                .contains("debug: false")
                .doesNotContainPattern("(?m)^    username: [^$\\s].*@gmail\\.com\\s*$");
    }

    private String resource(String path) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing test resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
