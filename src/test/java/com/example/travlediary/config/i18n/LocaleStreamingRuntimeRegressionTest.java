package com.example.travlediary.config.i18n;

import com.example.travlediary.config.GlobalRequestControllerAdvice;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = LocaleStreamingRuntimeRegressionTest.TestApplication.class,
        properties = {
                "spring.main.banner-mode=off",
                "logging.level.root=ERROR"
        })
class LocaleStreamingRuntimeRegressionTest {

    @LocalServerPort
    private int port;

    @Test
    void firstGuestRequestCompletesAfterRenderingLocalePostForms() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            fail("guest HTML response ended before the chunked body completed", exception);
            return;
        }

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("class=\"locale-option-form\"")
                .contains("id=\"locale-runtime-render-complete\"")
                .contains("</html>");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            MybatisAutoConfiguration.class,
            MailSenderAutoConfiguration.class
    })
    @Import({
            I18nConfig.class,
            GlobalRequestControllerAdvice.class,
            LocaleRuntimeProbeController.class
    })
    static class TestApplication {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .build();
        }
    }

    @Controller
    static class LocaleRuntimeProbeController {

        @GetMapping("/")
        String probe(Model model) {
            model.addAttribute("isLoggedIn", false);
            model.addAttribute("pageTitle", "Locale runtime probe");
            return "locale-runtime-probe";
        }
    }
}
