package com.example.travlediary.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * 요청 하나의 실제 클라이언트 주소를 정하는 한 곳.
 *
 * <p>로그인 실패 제한과 회원 관련 요청 제한, 번역 요청 제한이 모두 이 판단을 함께 쓴다.
 * 한쪽만 전달 머리말을 믿으면 같은 사람이 제한마다 다른 주소로 세어져 한도가 어긋난다.
 *
 * <p>머리말 해석은 여기 말고 어디에서도 하지 않는다. Controller 와 Filter 는
 * {@link #of(HttpServletRequest)} 로 <b>결과만</b> 읽는다.
 *
 * <h2>언제 전달 머리말을 믿는가</h2>
 * <ul>
 *   <li>{@link Mode#DIRECT} — 믿지 않는다. 늘 {@code request.getRemoteAddr()} 다.
 *       머리말이 붙어 있어도 읽지 않는다.</li>
 *   <li>{@link Mode#CLOUDFLARE_TUNNEL} — <b>이번 요청이 실제로 신뢰된 경로에서 들어왔을 때만</b>
 *       {@code CF-Connecting-IP} 를 읽는다. 머리말이 있다는 것만으로 Cloudflare 요청이라고
 *       보지 않는다. 주소가 하나의 올바른 IP 로 읽히지 않으면 접속 주소로 되돌아간다.</li>
 * </ul>
 *
 * <p>즉 어떤 모드에서도 "머리말을 붙였다" 만으로는 주소를 바꿀 수 없다. 바꾸려면 신뢰된
 * 경로에서 접속하는 것이 먼저이고, 그 경로는 설정이 정한다.
 */
public class ClientIpResolver {

    /**
     * 결과를 담아 두는 요청 속성. 필터가 요청당 한 번만 정하고 나머지는 이 값을 읽는다.
     * 값이 없으면(필터를 지나지 않은 요청) 접속 주소를 그대로 쓴다 — 즉 DIRECT 와 같다.
     */
    static final String REQUEST_ATTRIBUTE = ClientIpResolver.class.getName() + ".CLIENT_IP";

    /** Cloudflare 가 원 요청자의 주소를 담아 주는 머리말. */
    private static final String CLOUDFLARE_CLIENT_IP_HEADER = "CF-Connecting-IP";

    /** 주소를 끝내 알 수 없을 때 쓰는 값. 제한 키가 null 이 되지 않게 한다. */
    private static final String UNKNOWN = "unknown";

    private final Mode mode;
    private final TrustedProxies trustedProxies;

    public ClientIpResolver(Mode mode, String trustedProxyDefinitions) {
        this.mode = mode == null ? Mode.DIRECT : mode;
        this.trustedProxies = TrustedProxies.parse(trustedProxyDefinitions);
    }

    /**
     * 이 요청의 클라이언트 주소.
     *
     * <p>필터가 정해 둔 값이 있으면 그 값이고, 없으면 접속 주소다. 읽기만 하므로 화면 코드와
     * 제한 코드가 같은 값을 본다.
     */
    public static String of(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        Object resolved = request.getAttribute(REQUEST_ATTRIBUTE);
        if (resolved instanceof String clientIp && !clientIp.isBlank()) {
            return clientIp;
        }
        return remoteAddress(request);
    }

    /** 정책을 적용해 주소를 정한다. 요청당 한 번, 필터에서만 부른다. */
    public String resolve(HttpServletRequest request) {
        String remoteAddress = remoteAddress(request);
        if (mode == Mode.DIRECT || trustedProxies.isEmpty()) {
            return remoteAddress;
        }
        // 신뢰된 경로에서 들어온 요청이 아니면 머리말을 읽지 않는다.
        if (!trustedProxies.contains(remoteAddress)) {
            return remoteAddress;
        }
        return forwardedClientIp(request).orElse(remoteAddress);
    }

    /**
     * 전달 머리말이 담고 있는 주소.
     *
     * <p>주소 하나로 읽히는 올바른 값일 때만 쓴다. 콤마로 이어 붙인 목록, 포트가 붙은 값,
     * 공백이나 제어문자가 섞인 값, 지나치게 긴 값은 모두 버리고 접속 주소로 되돌아간다.
     */
    private Optional<String> forwardedClientIp(HttpServletRequest request) {
        return IpAddresses.canonicalize(request.getHeader(CLOUDFLARE_CLIENT_IP_HEADER));
    }

    /** 컨테이너가 본 접속 주소. 표기가 흔들리지 않게 표준형으로 맞춘다. */
    private static String remoteAddress(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return UNKNOWN;
        }
        return IpAddresses.canonicalize(remoteAddress).orElse(UNKNOWN);
    }

    /** 앞에 무엇이 서 있는지. 설정으로만 바뀐다. */
    public enum Mode {
        /** 프록시 없이 직접 받는다. 전달 머리말을 전혀 믿지 않는다. (기본값) */
        DIRECT,
        /**
         * Cloudflare Tunnel 뒤에서 받는다.
         * 신뢰된 경로에서 들어온 요청의 {@code CF-Connecting-IP} 만 믿는다.
         */
        CLOUDFLARE_TUNNEL
    }
}
