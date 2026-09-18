package com.example.travlediary.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static com.example.travlediary.security.ClientIpResolver.Mode.CLOUDFLARE_TUNNEL;
import static com.example.travlediary.security.ClientIpResolver.Mode.DIRECT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 프록시 뒤에서도 제한이 사람 단위로 나뉘는지.
 *
 * <p>여기서 보는 것은 판별 결과가 실제 제한에 그대로 이어지는지다. 로그인 제한과 요청 제한이
 * 같은 값을 쓰지 않으면, 한쪽은 사람별로 세고 다른 쪽은 프록시 하나로 세는 상태가 된다.
 *
 * <p>프록시 뒤에서 이 판별이 없으면 모든 사용자가 loopback 주소 하나로 묶여, 한 사람이
 * 로그인을 몇 번 틀리면 그 순간 전체 사용자가 함께 막힌다.
 */
class ClientIpThrottleIntegrationTest {

    private static final String LOOPBACK_PROXIES = "127.0.0.0/8,::1";

    /** 터널 뒤의 서로 다른 사용자는 로그인 실패 제한을 나눠 갖지 않는다. */
    @Test
    void twoClientsBehindTheTunnelDoNotShareTheLoginBlock() throws Exception {
        ClientIpFilter filter = new ClientIpFilter(
                new ClientIpResolver(CLOUDFLARE_TUNNEL, LOOPBACK_PROXIES));
        LoginThrottle throttle = new LoginThrottle();

        // 한 사람이 IP 단위 차단에 걸릴 만큼 실패한다
        for (int attempt = 0; attempt < LoginThrottle.IP_FAILURE_LIMIT; attempt++) {
            throttle.recordFailure("victim" + attempt,
                    clientIpOf(filter, "127.0.0.1", "203.0.113.10"));
        }

        assertThat(throttle.ipStatus(clientIpOf(filter, "127.0.0.1", "203.0.113.10")).blocked())
                .isTrue();
        // 같은 터널을 지나온 다른 사용자는 그대로 로그인할 수 있다
        assertThat(throttle.ipStatus(clientIpOf(filter, "127.0.0.1", "198.51.100.20")).blocked())
                .isFalse();
    }

    /**
     * 판별이 없을 때(DIRECT 로 프록시 뒤에 두는 잘못된 구성) 어떤 일이 생기는지도 함께 고정한다.
     * 한 사람의 실패가 터널을 지나온 모두를 막는다 — 이것이 이번 작업이 없앤 상태다.
     */
    @Test
    void withoutTheForwardedAddressEveryoneBehindTheProxyShareOneBlock() throws Exception {
        ClientIpFilter filter = new ClientIpFilter(new ClientIpResolver(DIRECT, LOOPBACK_PROXIES));
        LoginThrottle throttle = new LoginThrottle();

        for (int attempt = 0; attempt < LoginThrottle.IP_FAILURE_LIMIT; attempt++) {
            throttle.recordFailure("victim" + attempt,
                    clientIpOf(filter, "127.0.0.1", "203.0.113.10"));
        }

        assertThat(throttle.ipStatus(clientIpOf(filter, "127.0.0.1", "198.51.100.20")).blocked())
                .isTrue();
    }

    /** 회원 존재 확인·복구 메일 제한도 같은 판별을 쓴다. */
    @Test
    void theAccountGuardSeesTheSameClientAddresses() throws Exception {
        ClientIpFilter filter = new ClientIpFilter(
                new ClientIpResolver(CLOUDFLARE_TUNNEL, LOOPBACK_PROXIES));
        InMemoryAccountAbuseGuard guard = new InMemoryAccountAbuseGuard();

        String first = clientIpOf(filter, "127.0.0.1", "203.0.113.10");
        String second = clientIpOf(filter, "127.0.0.1", "198.51.100.20");
        for (int attempt = 0; attempt < InMemoryAccountAbuseGuard.EXISTENCE_LOOKUP_LIMIT;
             attempt++) {
            guard.checkExistenceLookup(first);
        }

        assertThatThrownBy(() -> guard.checkExistenceLookup(first))
                .isInstanceOf(TooManyAccountRequestsException.class);
        assertThatCode(() -> guard.checkExistenceLookup(second)).doesNotThrowAnyException();
    }

    /** 같은 사용자가 여러 요청을 보내면 예전처럼 하나의 한도를 나눠 쓴다. */
    @Test
    void oneClientStillShareTheirOwnLimitAcrossRequests() throws Exception {
        ClientIpFilter filter = new ClientIpFilter(
                new ClientIpResolver(CLOUDFLARE_TUNNEL, LOOPBACK_PROXIES));
        InMemoryAccountAbuseGuard guard = new InMemoryAccountAbuseGuard();

        for (int attempt = 0; attempt < InMemoryAccountAbuseGuard.EXISTENCE_LOOKUP_LIMIT;
             attempt++) {
            guard.checkExistenceLookup(clientIpOf(filter, "127.0.0.1", "203.0.113.10"));
        }

        assertThatThrownBy(() ->
                guard.checkExistenceLookup(clientIpOf(filter, "127.0.0.1", "203.0.113.10")))
                .isInstanceOf(TooManyAccountRequestsException.class);
    }

    /**
     * 애플리케이션 포트가 외부에 직접 열려 있다면, 머리말을 붙여도 주소가 나뉘지 않는다.
     * 터널을 지나지 않은 요청은 접속 주소 하나로 묶여 제한을 빠져나갈 수 없다.
     */
    @Test
    void forgedHeadersFromOutsideTheTunnelCannotSplitTheLimit() throws Exception {
        ClientIpFilter filter = new ClientIpFilter(
                new ClientIpResolver(CLOUDFLARE_TUNNEL, LOOPBACK_PROXIES));
        InMemoryAccountAbuseGuard guard = new InMemoryAccountAbuseGuard();

        for (int attempt = 0; attempt < InMemoryAccountAbuseGuard.EXISTENCE_LOOKUP_LIMIT;
             attempt++) {
            // 매번 다른 주소를 지어내 붙여도 실제 접속 주소가 그대로라 한 통에 쌓인다
            guard.checkExistenceLookup(
                    clientIpOf(filter, "203.0.113.99", "198.51.100." + (attempt % 250)));
        }

        assertThatThrownBy(() -> guard.checkExistenceLookup(
                clientIpOf(filter, "203.0.113.99", "198.51.100.251")))
                .isInstanceOf(TooManyAccountRequestsException.class);
    }

    /* ===== 도우미 ===== */

    /** 요청 하나를 필터에 통과시키고, 이후 코드가 읽게 될 주소를 돌려준다. */
    private String clientIpOf(ClientIpFilter filter, String remoteAddress, String forwarded)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        if (forwarded != null) {
            request.addHeader("CF-Connecting-IP", forwarded);
        }
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return ClientIpResolver.of(request);
    }
}
