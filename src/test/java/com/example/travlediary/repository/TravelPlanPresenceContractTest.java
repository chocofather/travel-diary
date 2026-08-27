package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실시간 접속 표시의 설정 / 화면 계약.
 * 방마다 topic 을 나누고, 화면에는 계정 정보가 오르지 않는다.
 */
class TravelPlanPresenceContractTest {

    @Test
    void onlyTheWebsocketStarterAndAStompClientWereAdded() throws IOException {
        String buildGradle = Files.readString(Path.of("build.gradle"), StandardCharsets.UTF_8);

        assertThat(buildGradle)
                .contains("org.springframework.boot:spring-boot-starter-websocket")
                // CDN 대신 WebJar 로 함께 배포한다
                .contains("org.webjars.npm:stomp__stompjs");
        // 이번 단계에 필요 없는 것들
        assertThat(buildGradle)
                .doesNotContain("redis")
                .doesNotContain("kafka")
                .doesNotContain("sockjs")
                .doesNotContain("jjwt");
    }

    @Test
    void theEndpointIsPlainWebSocketWithAMemoryBroker() throws IOException {
        String config = source("config/WebSocketConfig.java");

        assertThat(config)
                .contains("@EnableWebSocketMessageBroker")
                .contains("ENDPOINT = \"/ws\"")
                .contains("registry.addEndpoint(ENDPOINT)")
                // 방 전체 알림(/topic)과 요청자에게만 가는 답(/user/queue) 둘 다 필요하다
                .contains("enableSimpleBroker(\"/topic\", \"/queue\")")
                .contains("setApplicationDestinationPrefixes(\"/app\")")
                // 구독 검사를 붙여 둔다
                .contains("registration.interceptors(travelPlanWebSocketAuthInterceptor)");
        // SockJS 폴백은 두지 않는다
        assertThat(config).doesNotContain("withSockJS");
    }

    @Test
    void eachRoomGetsItsOwnTopic() throws IOException {
        String destinations = source("service/travelplan/TravelPlanPresenceDestinations.java");

        assertThat(destinations)
                .contains("/topic/travel-plans/%d/presence")
                .contains("^/topic/travel-plans/(\\\\d+)/presence$");
        // 모든 방을 한 topic 에 섞지 않는다
        assertThat(destinations).doesNotContain("/topic/presence\"");
    }

    @Test
    void membershipIsCheckedTheSameWayAsOverHttp() throws IOException {
        String access = source("service/travelplan/TravelPlanRoomAccess.java");

        // HTTP 쪽과 같은 조회를 그대로 쓴다
        assertThat(access)
                .contains("findPlanByIdAndStatus(")
                .contains("TravelPlanStatus.ACTIVE.name()")
                .contains("findMemberByPlanAndUser(")
                .contains("TravelPlanMemberStatus.ACTIVE.name()");
        // 신분은 로그인 세션에서만 온다
        assertThat(access)
                .contains("instanceof Authentication")
                .contains("CustomUserDetails")
                .doesNotContain("JWT")
                .doesNotContain("Jwt");
    }

    @Test
    void theHandshakeReusesTheLoginSessionAndStaysAuthenticated() throws IOException {
        String securityConfig = Files.readString(
                Path.of("src/main/java/com/example/travlediary/config/SecurityConfig.java"),
                StandardCharsets.UTF_8);

        // /ws 를 공개하지 않는다. 핸드셰이크가 기존 인증을 그대로 지나가야 한다
        assertThat(securityConfig).doesNotContain("\"/ws\"").doesNotContain("/ws/**");
        // 정적 STOMP 라이브러리만 열어 둔다
        assertThat(securityConfig).contains("\"/webjars/**\"");
    }

    @Test
    void everyMemberRowCarriesAPresenceMarker() throws IOException {
        String members = membersHtml();

        assertThat(members)
                .contains("data-travel-plan-member-row")
                .contains("data-member-id=${member.memberId}")
                .contains("class=\"travel-plan-presence-dot\"")
                .contains("data-travel-plan-presence-dot")
                // 점만으로 뜻이 전해지지 않게 한다
                .contains("aria-label=\"오프라인\"")
                .contains("title=\"오프라인\"");

        // 계정 정보는 화면에 오르지 않는다
        for (String personal : new String[]{"userId", "user_id", "username", "email", "nickname"}) {
            assertThat(members).as("개인정보 노출: %s", personal).doesNotContain(personal);
            assertThat(detailHtml()).as("개인정보 노출: %s", personal).doesNotContain(personal);
        }
    }

    @Test
    void theHeadcountAndTheOnlineCountStaySeparate() throws IOException {
        String members = membersHtml();

        // 참여자 총원은 그대로 둔다
        assertThat(members).contains("${memberCount} + ' / ' + ${memberLimit}");
        // 접속 인원은 따로 표시하고, 연결되기 전에는 숨겨 둔다
        assertThat(members)
                .contains("class=\"travel-plan-online-count\" hidden")
                .contains("data-travel-plan-online-count");
        // 팝오버를 열지 않아도 보이는 총원. 접속 인원과 다른 숫자다
        assertThat(detailHtml()).contains("'참여자 ' + ${travelPlan.memberCount} + '/'");
        assertThat(detailHtml()).doesNotContain("data-travel-plan-online-count");
    }

    @Test
    void presenceIsItsOwnScriptAndNeverSendsWhoYouAre() throws IOException {
        String detail = detailHtml();
        String presence = resource("/static/js/travel-plan-realtime.js");
        String scheduler = resource("/static/js/travel-plan-scheduler.js");

        assertThat(detail)
                .contains("/webjars/stomp__stompjs/7.0.0/bundles/stomp.umd.min.js")
                .contains("/js/travel-plan-realtime.js");

        // 방 번호는 화면의 data 속성에서 읽는다
        assertThat(presence)
                .contains("planner.getAttribute(\"data-plan-id\")")
                .contains("/topic/travel-plans/${planId}/presence")
                .contains("/app/travel-plans/${planId}/presence/join");
        // 신분은 서버가 세션에서 정한다. 클라이언트가 보내지 않는다
        // (보내는 값은 객체 필드 꼴로 나타난다. 화면의 aria 속성과는 다르다)
        assertThat(presence)
                .doesNotContain("userId")
                .doesNotContain("memberId:")
                .doesNotContain("role:")
                .doesNotContain("displayName:");

        // 일정 편집 스크립트는 실시간 연결을 모른다
        assertThat(scheduler)
                .doesNotContain("StompJs")
                .doesNotContain("WebSocket");
    }

    @Test
    void theClientReconnectsWithTheLibraryRatherThanItsOwnLoop() throws IOException {
        String presence = resource("/static/js/travel-plan-realtime.js");

        assertThat(presence)
                .contains("new StompJs.Client")
                .contains("reconnectDelay")
                .contains("heartbeatIncoming")
                // 다시 붙을 때마다 구독과 인사를 새로 한다
                .contains("client.onConnect")
                .contains("client.subscribe(")
                .contains("client.publish(");
        // 직접 만든 재시도 루프는 두지 않는다
        // (잠금 응답을 기다리는 한 번짜리 timeout 은 재시도가 아니다)
        assertThat(presence).doesNotContain("setInterval");
    }

    @Test
    void realtimeStopsAtPresenceScheduleAndTheEditingSpot() throws IOException {
        String realtime = resource("/static/js/travel-plan-realtime.js");

        // 커서 공유·입력 중 표시 같은 것은 아직 없다
        // (채팅·투표는 들어왔지만 이 파일은 연결만 맡고 화면은 각 모듈이 맡는다)
        for (String notYet : new String[]{"typing", "cursor", "selection"}) {
            assertThat(realtime).as("아직 없는 기능: %s", notYet).doesNotContain(notYet);
        }
        // 투표도 구독만 여기 있고 표 집계는 이 파일에 없다
        assertThat(realtime)
                .doesNotContain("vote")
                .doesNotContain("POLL_CLOSED");
        // WebSocket 으로 저장하지 않는다. 저장은 기존 HTTP 경로 그대로다
        assertThat(realtime)
                .doesNotContain("client.publish({ destination: `/app/travel-plans/${planId}/items")
                .doesNotContain("method: \"POST\"");
    }

    private String detailHtml() throws IOException {
        return resource("/templates/travelplan/detail.html");
    }

    /** 참여자 줄은 명단이 바뀔 때 통째로 새로 그려지므로 조각 쪽에 있다. */
    private String membersHtml() throws IOException {
        return resource("/templates/travelplan/fragments/members.html");
    }

    private String source(String relativePath) throws IOException {
        return Files.readString(
                Path.of("src/main/java/com/example/travlediary/" + relativePath),
                StandardCharsets.UTF_8);
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
