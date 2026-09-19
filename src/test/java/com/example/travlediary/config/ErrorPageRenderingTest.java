package com.example.travlediary.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = ErrorPageRenderingTest.ErrorPageApplication.class,
        properties = {
                "spring.main.banner-mode=off",
                "logging.level.root=ERROR"
        })
class ErrorPageRenderingTest {

    @LocalServerPort
    private int port;

    @Test
    void missingHtmlPageUsesTheTripbora404View() throws Exception {
        HttpResponse<String> response = get("/error-page-probe/missing", MediaType.TEXT_HTML_VALUE);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("content-type")).hasValueSatisfying(contentType ->
                assertThat(contentType).contains(MediaType.TEXT_HTML_VALUE));
        assertThat(response.body())
                .contains("Tripbora")
                .contains("페이지를 찾을 수 없어요.")
                .doesNotContain("Whitelabel Error Page")
                .doesNotContain("exception")
                .doesNotContain("trace");
    }

    @Test
    void serverFailureUsesTheSafe500ViewWithoutInternalDetails() throws Exception {
        HttpResponse<String> response = get("/error-page-probe/failure", MediaType.TEXT_HTML_VALUE);

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body())
                .contains("Tripbora")
                .contains("일시적인 문제가 발생했어요.")
                .doesNotContain("Whitelabel Error Page")
                .doesNotContain("IllegalStateException")
                .doesNotContain("simulated internal failure");
    }

    @Test
    void jsonErrorRequestsRemainJsonInsteadOfUsingTheHtmlErrorView() throws Exception {
        HttpResponse<String> response = get("/error-page-probe/missing", MediaType.APPLICATION_JSON_VALUE);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("content-type")).hasValueSatisfying(contentType ->
                assertThat(contentType).contains(MediaType.APPLICATION_JSON_VALUE));
        assertThat(response.body()).doesNotContain("Tripbora");
    }

    private HttpResponse<String> get(String path, String accept) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Accept", accept)
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            MailSenderAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            OAuth2ClientAutoConfiguration.class
    })
    static class ErrorPageApplication {

        @Bean
        ErrorPageProbeController errorPageProbeController() {
            return new ErrorPageProbeController();
        }
    }

    @Controller
    static class ErrorPageProbeController {

        @GetMapping("/error-page-probe/failure")
        void fail() {
            throw new IllegalStateException("simulated internal failure");
        }
    }
}
