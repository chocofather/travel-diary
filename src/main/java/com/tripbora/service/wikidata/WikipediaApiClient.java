package com.tripbora.service.wikidata;

import com.fasterxml.jackson.databind.JsonNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

@Component
public class WikipediaApiClient {
    private static final int MAX_SCRIPT_TEXTS = 2;
    /** Wikidata 설명 한도(250자). */
    private static final int MAX_SCRIPT_TEXT_LENGTH = 250;
    /** 변환 요청에 넣으면 문장이 아니라 위키 문법으로 해석될 수 있는 표기. */
    private static final Pattern WIKITEXT_MARKUP = Pattern.compile(
            "\\[\\[|]]|\\{\\{|}}|''|[<>&]|~~~|__|-\\{|}-|----|^[\\s*#:;=]");
    private final Map<String, RestClient> clients;
    private final ExternalApiRateLimiter rateLimiter;

    @Autowired
    public WikipediaApiClient(RestClient.Builder builder, ExternalApiRateLimiter rateLimiter) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.clients = Map.of(
                "ko", client(builder, requestFactory, "ko"),
                "en", client(builder, requestFactory, "en"),
                "ja", client(builder, requestFactory, "ja"),
                "zh", client(builder, requestFactory, "zh"));
        this.rateLimiter = rateLimiter;
    }

    WikipediaApiClient(Map<String, RestClient> clients) {
        this(clients, ExternalApiRateLimiter.singleAttempt());
    }

    WikipediaApiClient(Map<String, RestClient> clients, ExternalApiRateLimiter rateLimiter) {
        this.clients = clients;
        this.rateLimiter = rateLimiter;
    }

    private RestClient client(RestClient.Builder builder, SimpleClientHttpRequestFactory requestFactory, String language) {
        return builder.clone().baseUrl("https://" + language + ".wikipedia.org")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "TripBoraWikipediaPreview/1.0 (https://github.com/chocofather/tripbora)")
                .build();
    }

    public JsonNode getPage(String language, String title, String variant) {
        RestClient client = clients.get(language);
        if (client == null || title == null || title.isBlank()
                || (variant != null && !(language.equals("zh")
                && (variant.equals("zh-cn") || variant.equals("zh-tw"))))) {
            throw new IllegalArgumentException("올바르지 않은 Wikipedia 문서 요청입니다.");
        }
        JsonNode response = request(client, builder -> {
            var uri = builder.path("/w/api.php")
                    .queryParam("action", "query")
                    .queryParam("prop", "extracts|pageprops|info")
                    .queryParam("meta", "siteinfo")
                    .queryParam("siprop", "rightsinfo")
                    .queryParam("inprop", "url|varianttitles")
                    .queryParam("exintro", 1)
                    .queryParam("explaintext", 1)
                    .queryParam("exchars", 1200)
                    .queryParam("titles", title)
                    .queryParam("format", "json")
                    .queryParam("formatversion", 2);
            if (variant != null) uri.queryParam("variant", variant);
            return uri.build();
        });
        if (!response.path("query").path("pages").isArray()) {
            throw new WikipediaApiException("Wikipedia 응답을 해석하지 못했습니다.");
        }
        return response;
    }

    /**
     * 중국어 Wikipedia의 표기 변환기로 짧은 문장들을 간체(zh-hans) 또는 번체(zh-hant)로 바꾼 평문을 같은 순서로 돌려준다.
     * 여러 문장은 문단으로 나눠 한 번의 요청으로 보낸다. 위키 문법으로 해석될 수 있는 문장은 보내지 않고,
     * 결과 문단 수가 맞지 않으면 실패로 본다.
     */
    public List<String> convertChineseScript(List<String> texts, String variant) {
        RestClient client = clients.get("zh");
        if (client == null || texts == null || texts.isEmpty() || texts.size() > MAX_SCRIPT_TEXTS
                || !("zh-hans".equals(variant) || "zh-hant".equals(variant))
                || texts.stream().anyMatch(text -> text == null || text.isBlank()
                || text.length() > MAX_SCRIPT_TEXT_LENGTH || text.chars().anyMatch(Character::isISOControl)
                || WIKITEXT_MARKUP.matcher(text).find())) {
            throw new IllegalArgumentException("올바르지 않은 중국어 표기 변환 요청입니다.");
        }
        String joined = String.join("\n\n", texts);
        JsonNode response = request(client, builder -> builder.path("/w/api.php")
                .queryParam("action", "parse")
                .queryParam("contentmodel", "wikitext")
                .queryParam("prop", "text")
                .queryParam("disablelimitreport", 1)
                .queryParam("variant", variant)
                .queryParam("text", "{text}")
                .queryParam("format", "json")
                .queryParam("formatversion", 2)
                .build(joined));
        String html = response.path("parse").path("text").asText(null);
        Element output = html == null ? null : Jsoup.parse(html).selectFirst("div.mw-parser-output");
        if (output == null || output.children().size() != texts.size()
                || output.children().stream().anyMatch(child -> !child.tagName().equals("p"))) {
            throw new WikipediaApiException("Wikipedia 응답을 해석하지 못했습니다.");
        }
        return output.children().stream().map(Element::text).toList();
    }

    /** 언어가 달라도 같은 Wikimedia 제한을 받으므로 대기 상태는 Wikipedia 하나로 공유한다. */
    private JsonNode request(RestClient client, Function<UriBuilder, URI> uri) {
        return rateLimiter.call(ExternalApiRateLimiter.Service.WIKIPEDIA, () -> send(client, uri),
                WikipediaRateLimitException::new);
    }

    private JsonNode send(RestClient client, Function<UriBuilder, URI> uri) {
        try {
            JsonNode response = client.get().uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(JsonNode.class);
            String errorCode = response == null ? "" : response.path("error").path("code").asText("");
            if (errorCode.isBlank() && response != null && response.path("errors").isArray()
                    && !response.path("errors").isEmpty()) {
                errorCode = response.path("errors").get(0).path("code").asText("");
            }
            if ("ratelimited".equals(errorCode) || "maxlag".equals(errorCode)) {
                throw new ExternalApiRateLimiter.Limited(null);
            }
            if (response == null || response.has("error") || response.has("errors")) {
                throw new WikipediaApiException("Wikipedia 응답을 해석하지 못했습니다.");
            }
            return response;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 429 || status == 503) {
                throw new ExternalApiRateLimiter.Limited(ExternalApiRateLimiter.parseRetryAfter(
                        exception.getResponseHeaders() == null ? null
                                : exception.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER)));
            }
            if (status == 403) {
                throw new WikipediaApiException(RateLimitMessages.forbidden("Wikipedia"), exception);
            }
            throw new WikipediaApiException("Wikipedia 정보를 불러오지 못했습니다.", exception);
        } catch (ResourceAccessException exception) {
            if (exception.getMostSpecificCause() instanceof SocketTimeoutException) {
                throw new WikipediaApiException("Wikipedia 응답 시간이 초과되었습니다. 다시 시도해 주세요.", exception);
            }
            throw new WikipediaApiException("Wikipedia에 연결하지 못했습니다.", exception);
        } catch (RestClientException exception) {
            throw new WikipediaApiException("Wikipedia에 연결하지 못했습니다.", exception);
        }
    }
}
