package com.example.travlediaryproxy;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = ProductionForwardedHeadersTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
class ProductionForwardedHeadersTest {

    @LocalServerPort
    private int port;

    @Test
    void loginFailureRedirectKeepsTheVisitorsHttpsScheme() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/redirect-probe"))
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "tripbora.com")
                .GET()
                .build();

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<Void> response = client.send(
                request, HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location"))
                .hasValue("https://tripbora.com/login?error=true");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            OAuth2ClientWebSecurityAutoConfiguration.class,
            SecurityAutoConfiguration.class
    })
    @Import(RedirectProbe.class)
    static class TestApplication {
    }

    @RestController
    static class RedirectProbe {
        @GetMapping("/redirect-probe")
        void redirect(HttpServletResponse response) throws java.io.IOException {
            response.sendRedirect("/login?error=true");
        }
    }
}
