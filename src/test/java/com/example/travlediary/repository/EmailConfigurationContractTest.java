package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

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
                .contains("server-url: ${APP_BASE_URL:http://localhost:8081}")
                .contains("connectiontimeout: 5000")
                .contains("timeout: 5000")
                .contains("writetimeout: 5000")
                .contains("debug: false")
                .doesNotContainPattern("(?m)^    username: [^$\\s].*@gmail\\.com\\s*$");
    }

    @Test
    void developmentAndProductionProfilesUseSeparateServerPorts() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        var development = loader.load(
                "development", new ClassPathResource("application.yml")).get(0);
        var production = loader.load(
                "production", new ClassPathResource("application-prod.yml")).get(0);

        assertThat(development.getProperty("server.port")).isEqualTo(8081);
        assertThat(development.getProperty("server.address")).isEqualTo("127.0.0.1");
        assertThat(production.getProperty("server.port")).isEqualTo(8080);
        assertThat(production.getProperty("server.address"))
                .isEqualTo("${SERVER_ADDRESS:127.0.0.1}");
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
