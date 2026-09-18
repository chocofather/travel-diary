package com.example.travlediary.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static com.example.travlediary.security.ClientIpResolver.Mode.CLOUDFLARE_TUNNEL;
import static com.example.travlediary.security.ClientIpResolver.Mode.DIRECT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 클라이언트 주소를 언제 믿고 언제 믿지 않는지의 계약.
 *
 * <p>여기서 잘못 판단하면 두 방향으로 모두 무너진다 — 머리말을 함부로 믿으면 공격자가 주소를
 * 지어내 제한을 빠져나가고, 프록시 뒤에서 머리말을 아예 무시하면 모든 사용자가 프록시 주소
 * 하나로 묶여 한 사람 때문에 전체가 막힌다.
 */
class ClientIpResolverTest {

    private static final String LOOPBACK_PROXIES = "127.0.0.0/8,::1";

    /* ===== DIRECT: 어떤 머리말도 믿지 않는다 ===== */

    @Test
    void directModeAlwaysUsesTheActualConnectionAddress() {
        ClientIpResolver resolver = new ClientIpResolver(DIRECT, LOOPBACK_PROXIES);

        assertThat(resolver.resolve(requestFrom("203.0.113.10"))).isEqualTo("203.0.113.10");
    }

    /**
     * 머리말을 지어내 붙여도 주소가 바뀌지 않는다.
     * loopback 처럼 "믿을 만해 보이는" 경로에서 들어와도 DIRECT 에서는 읽지 않는다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"CF-Connecting-IP", "X-Forwarded-For", "X-Real-IP"})
    void directModeIgnoresForgedForwardingHeaders(String header) {
        ClientIpResolver resolver = new ClientIpResolver(DIRECT, LOOPBACK_PROXIES);

        MockHttpServletRequest fromOutside = requestFrom("203.0.113.10");
        fromOutside.addHeader(header, "198.51.100.77");
        assertThat(resolver.resolve(fromOutside)).isEqualTo("203.0.113.10");

        MockHttpServletRequest fromLoopback = requestFrom("127.0.0.1");
        fromLoopback.addHeader(header, "198.51.100.77");
        assertThat(resolver.resolve(fromLoopback)).isEqualTo("127.0.0.1");
    }

    /* ===== CLOUDFLARE_TUNNEL: 신뢰된 경로에서 온 CF 머리말만 믿는다 ===== */

    /** cloudflared 가 같은 기기의 loopback 으로 붙여 준 요청이라면 원 요청자 주소를 쓴다. */
    @Test
    void tunnelModeUsesTheForwardedClientIpFromATrustedPath() {
        ClientIpResolver resolver = new ClientIpResolver(CLOUDFLARE_TUNNEL, LOOPBACK_PROXIES);
        MockHttpServletRequest request = requestFrom("127.0.0.1");
        request.addHeader("CF-Connecting-IP", "203.0.113.10");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    /**
     * 신뢰되지 않은 경로에서 온 요청의 머리말은 읽지 않는다.
     *
     * <p>Cloudflare 머리말이 붙어 있다는 것만으로 Cloudflare 요청이라고 보지 않는다.
     * 애플리케이션 포트가 외부에 직접 열려 있다면 누구나 이 머리말을 붙일 수 있다.
     */
    @Test
    void tunnelModeIgnoresTheHeaderWhenTheRequestDidNotComeThroughATrustedPath() {
        ClientIpResolver resolver = new ClientIpResolver(CLOUDFLARE_TUNNEL, LOOPBACK_PROXIES);
        MockHttpServletRequest request = requestFrom("203.0.113.99");
        request.addHeader("CF-Connecting-IP", "198.51.100.77");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.99");
    }

    /** 신뢰 목록이 비어 있으면 모드를 켜도 아무 경로도 믿지 않는다. */
    @Test
    void tunnelModeWithoutAnyTrustedProxyBehavesLikeDirect() {
        ClientIpResolver resolver = new ClientIpResolver(CLOUDFLARE_TUNNEL, "");
        MockHttpServletRequest request = requestFrom("127.0.0.1");
        request.addHeader("CF-Connecting-IP", "198.51.100.77");

        assertThat(resolver.resolve(request)).isEqualTo("127.0.0.1");
    }

    /** 신뢰된 경로여도 머리말이 주소 하나로 읽히지 않으면 접속 주소로 되돌아간다. */
    @ParameterizedTest
    @ValueSource(strings = {
            "203.0.113.10, 198.51.100.7",   // 콤마로 이어 붙인 목록
            "203.0.113.10:443",             // 포트가 붙은 값
            "203.0.113.10 198.51.100.7",    // 공백으로 이어 붙인 두 주소
            "not-an-ip",
            "999.1.1.1",                    // 범위를 벗어난 값
            "010.0.0.1",                    // 앞자리 0
            "2001:db8::1::2",               // :: 가 두 번
            "::ffff:1.2.3.4%eth0",          // 구역 식별자
            "1.2.3.4\n198.51.100.7",        // 제어문자
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void tunnelModeFallsBackWhenTheForwardedValueIsNotOneValidAddress(String forged) {
        ClientIpResolver resolver = new ClientIpResolver(CLOUDFLARE_TUNNEL, LOOPBACK_PROXIES);
        MockHttpServletRequest request = requestFrom("127.0.0.1");
        request.addHeader("CF-Connecting-IP", forged);

        assertThat(resolver.resolve(request)).isEqualTo("127.0.0.1");
    }

    /** 값 앞뒤의 공백은 프록시가 흔히 남기는 것이라 다듬어 받는다. (안쪽 공백은 위에서 걸린다) */
    @Test
    void surroundingWhitespaceInTheHeaderIsTrimmed() {
        ClientIpResolver resolver = new ClientIpResolver(CLOUDFLARE_TUNNEL, LOOPBACK_PROXIES);

        assertThat(resolveWithHeader(resolver, "  203.0.113.10  ")).isEqualTo("203.0.113.10");
    }

    /** 머리말이 아예 없으면 접속 주소를 쓴다. */
    @Test
    void tunnelModeFallsBackWhenTheHeaderIsAbsent() {
        ClientIpResolver resolver = new ClientIpResolver(CLOUDFLARE_TUNNEL, LOOPBACK_PROXIES);

        assertThat(resolver.resolve(requestFrom("127.0.0.1"))).isEqualTo("127.0.0.1");
    }

    /* ===== IPv6 ===== */

    @Test
    void ipv6LoopbackIsATrustedPathAndIpv6ClientsAreCarriedThrough() {
        ClientIpResolver resolver = new ClientIpResolver(CLOUDFLARE_TUNNEL, LOOPBACK_PROXIES);
        MockHttpServletRequest request = requestFrom("0:0:0:0:0:0:0:1");
        request.addHeader("CF-Connecting-IP", "2001:db8::1");

        // 표기가 흔들리지 않도록 표준형으로 돌려준다
        assertThat(resolver.resolve(request))
                .isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    /**
     * 같은 IPv6 주소를 다르게 적어도 같은 키가 된다.
     * 표기만 바꿔 가며 제한을 우회할 수 없어야 한다.
     */
    @Test
    void differentSpellingsOfOneIpv6AddressResolveToTheSameKey() {
        ClientIpResolver resolver = new ClientIpResolver(CLOUDFLARE_TUNNEL, LOOPBACK_PROXIES);

        assertThat(resolveWithHeader(resolver, "2001:db8::1"))
                .isEqualTo(resolveWithHeader(resolver,
                        "2001:0db8:0000:0000:0000:0000:0000:0001"))
                .isEqualTo(resolveWithHeader(resolver, "2001:DB8::1"));
    }

    /** IPv4 를 끼워 넣은 IPv6 표기는 IPv4 한 개로 모인다. */
    @Test
    void ipv4MappedAddressesResolveToThePlainIpv4Key() {
        ClientIpResolver resolver = new ClientIpResolver(CLOUDFLARE_TUNNEL, LOOPBACK_PROXIES);

        assertThat(resolveWithHeader(resolver, "::ffff:203.0.113.10")).isEqualTo("203.0.113.10");
    }

    /* ===== 신뢰 경로 지정 ===== */

    /** CIDR 로 적은 구간 전체가 신뢰된다. (Nginx/VPS 로 옮길 때 쓰는 형태) */
    @Test
    void aTrustedProxyCanBeGivenAsACidrRange() {
        ClientIpResolver resolver = new ClientIpResolver(CLOUDFLARE_TUNNEL, "10.8.0.0/16");
        MockHttpServletRequest inside = requestFrom("10.8.42.7");
        inside.addHeader("CF-Connecting-IP", "203.0.113.10");
        MockHttpServletRequest outside = requestFrom("10.9.42.7");
        outside.addHeader("CF-Connecting-IP", "203.0.113.10");

        assertThat(resolver.resolve(inside)).isEqualTo("203.0.113.10");
        assertThat(resolver.resolve(outside)).isEqualTo("10.9.42.7");
    }

    /** 읽을 수 없는 신뢰 경로 설정은 조용히 버린다. 넓게 믿는 쪽으로 기울지 않는다. */
    @Test
    void unreadableTrustedProxyEntriesAreDroppedInsteadOfWidened() {
        ClientIpResolver resolver = new ClientIpResolver(
                CLOUDFLARE_TUNNEL, "not-an-ip, 127.0.0.0/99, 0.0.0.0/0x, 127.0.0.0/8");
        MockHttpServletRequest loopback = requestFrom("127.0.0.1");
        loopback.addHeader("CF-Connecting-IP", "203.0.113.10");
        MockHttpServletRequest elsewhere = requestFrom("198.51.100.5");
        elsewhere.addHeader("CF-Connecting-IP", "203.0.113.10");

        assertThat(resolver.resolve(loopback)).isEqualTo("203.0.113.10");
        assertThat(resolver.resolve(elsewhere)).isEqualTo("198.51.100.5");
    }

    /* ===== 읽기 접근자 ===== */

    /** 필터가 정해 둔 값이 있으면 그 값을 읽는다. */
    @Test
    void theAccessorReadsWhatTheFilterDecided() {
        MockHttpServletRequest request = requestFrom("127.0.0.1");
        request.setAttribute(ClientIpResolver.REQUEST_ATTRIBUTE, "203.0.113.10");

        assertThat(ClientIpResolver.of(request)).isEqualTo("203.0.113.10");
    }

    /**
     * 필터를 지나지 않은 요청은 접속 주소를 쓴다.
     * 값이 없다고 머리말을 읽지는 않는다 — 빠뜨려도 DIRECT 와 같게 동작한다.
     */
    @Test
    void theAccessorFallsBackToTheConnectionAddressWithoutTrustingHeaders() {
        MockHttpServletRequest request = requestFrom("203.0.113.10");
        request.addHeader("CF-Connecting-IP", "198.51.100.77");
        request.addHeader("X-Forwarded-For", "198.51.100.77");

        assertThat(ClientIpResolver.of(request)).isEqualTo("203.0.113.10");
    }

    /* ===== 도우미 ===== */

    private String resolveWithHeader(ClientIpResolver resolver, String forwarded) {
        MockHttpServletRequest request = requestFrom("127.0.0.1");
        request.addHeader("CF-Connecting-IP", forwarded);
        return resolver.resolve(request);
    }

    private MockHttpServletRequest requestFrom(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
