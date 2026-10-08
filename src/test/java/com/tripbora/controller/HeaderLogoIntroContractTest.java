package com.tripbora.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 헤더 로고 등장 모션. 기존 로고 PNG 를 그대로 쓰고, 어떤 경우에도 정적 로고가 숨은 채 남지 않아야 한다.
 */
class HeaderLogoIntroContractTest {

    @Test
    void introRunsOncePerSessionAndAlwaysFallsBackToTheStaticLogo() throws IOException {
        String header = resource("/templates/fragments/header.html");

        assertThat(header)
                // 기존 로고 이미지·링크는 그대로
                .contains("<a href=\"/\"><img src=\"/images/branding/tripbora-logo6.png?v=20260928-1\"")
                // 움직임 줄이기·이미 본 세션·sessionStorage 사용 불가면 아무것도 하지 않는다
                .contains("prefers-reduced-motion: reduce")
                .contains("sessionStorage.getItem(\"tripbora.logoIntroPlayed\")")
                .contains("catch (error)")
                // 이미지가 늦거나 깨지면 정적 로고, 끝나면 클래스를 떼어 원래 로고만 남긴다
                .contains("setTimeout(finish, 2500)")
                .contains("}, finish);")
                .contains("event.animationName === \"tripbora-logo-reveal\"")
                .contains("setTimeout(finish, 2800)")
                .contains("classList.remove(\"is-logo-intro\", \"is-logo-intro-playing\")");
    }

    /** 복제 레이어 없이 기존 로고 img 하나만 움직이고, 한 번만 돈다. */
    @Test
    void motionIsShortCssOnlyAndMovesTheExistingLogoImage() throws IOException {
        String css = resource("/static/css/style.css");

        assertThat(css)
                .contains("@keyframes tripbora-logo-flight")
                .contains("@keyframes tripbora-logo-reveal")
                .contains("tripbora-logo-flight 1600ms linear backwards")
                .contains("tripbora-logo-reveal 2200ms linear both")
                .doesNotContain("infinite")
                .doesNotContain(".logo.is-logo-intro > a::before")
                .contains("@media (prefers-reduced-motion: reduce)");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
