package com.tripbora.service.wikidata;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

class WikipediaApiClientTest {
    @Test
    void requestsPlainTextWithTheActualChineseVariant() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://zh.wikipedia.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WikipediaApiClient client = new WikipediaApiClient(Map.of("zh", builder.build()));
        server.expect(requestTo(containsString("/w/api.php")))
                .andExpect(queryParam("titles", "%E8%89%BE%E8%8F%B2%E7%88%BE%E9%90%B5%E5%A1%94"))
                .andExpect(queryParam("variant", "zh-cn"))
                .andExpect(queryParam("explaintext", "1"))
                .andExpect(queryParam("prop", "extracts%7Cpageprops%7Cinfo"))
                .andExpect(queryParam("inprop", "url%7Cvarianttitles"))
                .andRespond(withSuccess("{\"query\":{\"pages\":[]}}", MediaType.APPLICATION_JSON));

        assertThat(client.getPage("zh", "艾菲爾鐵塔", "zh-cn").path("query").path("pages").isArray()).isTrue();
        server.verify();
    }

    @Test
    void chineseScriptConversionSendsTextsAsParagraphsInOneRequestAndReturnsPlainTextInOrder() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://zh.wikipedia.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WikipediaApiClient client = new WikipediaApiClient(Map.of("zh", builder.build()));
        server.expect(requestTo(containsString("/w/api.php")))
                .andExpect(queryParam("action", "parse"))
                .andExpect(queryParam("contentmodel", "wikitext"))
                .andExpect(queryParam("variant", "zh-hant"))
                // 괄호·공백이 있는 원문도 그대로 인코딩하고, 두 문장은 빈 줄로 나눈 문단으로 보낸다.
                .andExpect(requestTo(containsString("text=%E6%BE%B3%E5%A4%A7%E5%88%A9%E4%BA%9A%20%28Sydney%29%0A%0A%E5%BB%BA%E7%AD%91&")))
                .andRespond(withSuccess("{\"parse\":{\"text\":\"<div class=\\\"mw-parser-output\\\" lang=\\\"zh-Hant\\\">"
                        + "<p>澳大利亞 (Sydney)\\n</p><p>建築\\n</p></div>\"}}", MediaType.APPLICATION_JSON));
        // 문단 수가 맞지 않으면 실패로 본다.
        server.expect(requestTo(containsString("/w/api.php")))
                .andRespond(withSuccess("{\"parse\":{\"text\":\"<div class=\\\"mw-parser-output\\\"><p>建築</p></div>\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.convertChineseScript(List.of("澳大利亚 (Sydney)", "建筑"), "zh-hant"))
                .containsExactly("澳大利亞 (Sydney)", "建築");
        assertThatThrownBy(() -> client.convertChineseScript(List.of("建筑", "悉尼"), "zh-hant"))
                .isInstanceOf(WikipediaApiException.class);
        assertThatThrownBy(() -> client.convertChineseScript(List.of("建築"), "zh-tw"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.convertChineseScript(List.of("建".repeat(251)), "zh-hant"))
                .isInstanceOf(IllegalArgumentException.class);
        // 위키 문법으로 해석될 수 있는 문장은 보내지 않는다.
        assertThatThrownBy(() -> client.convertChineseScript(List.of("[[巴黎]]的塔"), "zh-hant"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.convertChineseScript(List.of("*列表"), "zh-hant"))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    void rateLimitAndMalformedResponseAreDistinctErrors() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://en.wikipedia.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WikipediaApiClient client = new WikipediaApiClient(Map.of("en", builder.build()));
        server.expect(requestTo(containsString("/w/api.php"))).andRespond(withTooManyRequests());
        server.expect(requestTo(containsString("/w/api.php")))
                .andRespond(withSuccess("{\"error\":{\"code\":\"maxlag\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/w/api.php")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getPage("en", "Eiffel Tower", null))
                .isInstanceOf(WikipediaApiException.class).hasMessageContaining("잠시 후");
        assertThatThrownBy(() -> client.getPage("en", "Eiffel Tower", null))
                .isInstanceOf(WikipediaApiException.class).hasMessageContaining("잠시 후");
        assertThatThrownBy(() -> client.getPage("en", "Eiffel Tower", null))
                .isInstanceOf(WikipediaApiException.class).hasMessageContaining("응답을 해석");
        server.verify();
    }
}
