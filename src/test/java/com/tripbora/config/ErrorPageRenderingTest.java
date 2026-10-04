package com.tripbora.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

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

    @Autowired
    private ServerProperties serverProperties;

    @Autowired
    private MultipartProperties multipartProperties;

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

    @Test
    void destinationFormWithMeasuredMultipartFieldCountIsAcceptedWithinABoundedLimit() throws Exception {
        assertThat(serverProperties.getTomcat().getMaxPartCount()).isEqualTo(600);
        // 관리자 여행지 사진 한 장 20MB, 요청 전체 25MB (사진 한 장 + 출처 입력값)
        assertThat(multipartProperties.getMaxFileSize().toBytes()).isEqualTo(20 * 1024 * 1024);
        assertThat(multipartProperties.getMaxRequestSize().toBytes()).isEqualTo(25 * 1024 * 1024);

        HttpResponse<String> response = postMultipart(494);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("accepted");
    }

    @Test
    void exceedingMultipartFieldLimitShowsAnActionable413Page() throws Exception {
        HttpResponse<String> response = postMultipart(601);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body())
                .contains("요청에 포함된 사진이나 입력 항목이 서버 한도를 넘었습니다")
                .doesNotContain("Whitelabel Error Page");
    }

    private HttpResponse<String> postMultipart(int fieldCount) throws Exception {
        String boundary = "destination-form-test-boundary";
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < fieldCount; index++) {
            body.append("--").append(boundary).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"field")
                    .append(index).append("\"\r\n\r\nvalue\r\n");
        }
        body.append("--").append(boundary).append("--\r\n");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/destinations"))
                .header("Accept", MediaType.TEXT_HTML_VALUE)
                .header("Content-Type", MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
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

        @Bean
        MultipartLimitExceptionAdvice multipartLimitExceptionAdvice() {
            return new MultipartLimitExceptionAdvice();
        }
    }

    @Controller
    static class ErrorPageProbeController {

        @GetMapping("/error-page-probe/failure")
        void fail() {
            throw new IllegalStateException("simulated internal failure");
        }

        @PostMapping(path = "/admin/destinations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @ResponseBody
        String acceptDestinationForm(@RequestParam("field0") String firstField) {
            return "accepted";
        }
    }
}
