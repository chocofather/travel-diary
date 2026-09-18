package com.example.travlediary.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

/**
 * IP 주소 문자열을 다루는 자리.
 *
 * <p>여기 들어오는 값은 프록시가 붙인 머리말처럼 <b>바깥에서 온 문자열</b>일 수 있다. 그래서
 * 이름 풀이(DNS)를 절대 하지 않는다. {@link InetAddress#getByName(String)} 는 리터럴이
 * 아닌 값을 받으면 이름을 풀려고 나가므로, 먼저 리터럴인지 직접 확인한 뒤에만 부른다.
 *
 * <p>요청 제한의 키로 쓰려면 같은 주소가 늘 같은 문자열이어야 한다. {@code 2001:db8::1} 과
 * {@code 2001:0db8:0000:0000:0000:0000:0000:0001} 이 다른 키가 되면 표기만 바꿔 가며
 * 한도를 우회할 수 있다. 그래서 확인을 마친 값은 언제나 표준형으로 바꿔 돌려준다.
 */
final class IpAddresses {

    /** IPv6 를 글자로 적었을 때의 최대 길이. (IPv4 를 끼워 넣은 형태까지 포함) */
    private static final int MAX_LENGTH = 45;

    private IpAddresses() {
    }

    /**
     * 주소 하나를 담은 리터럴인지 확인하고 표준형으로 바꾼다.
     *
     * <p>콤마로 이어 붙인 목록, 공백·제어문자가 섞인 값, 포트가 붙은 값, 너무 긴 값,
     * 구역 식별자(%eth0)가 붙은 값은 모두 여기서 걸린다.
     *
     * @return 표준형 주소. 주소 하나로 읽히지 않으면 비어 있음.
     */
    static Optional<String> canonicalize(String value) {
        return parse(value).map(InetAddress::getHostAddress);
    }

    /** 표준형 바이트. CIDR 비교에 쓴다. (IPv4 는 4바이트, IPv6 는 16바이트) */
    static Optional<byte[]> toBytes(String value) {
        return parse(value).map(InetAddress::getAddress);
    }

    private static Optional<InetAddress> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String candidate = value.strip();
        if (candidate.isEmpty() || candidate.length() > MAX_LENGTH || !isLiteral(candidate)) {
            return Optional.empty();
        }
        try {
            // 리터럴인 것을 확인했으므로 이름 풀이로 새어 나가지 않는다.
            return Optional.of(InetAddress.getByName(candidate));
        } catch (UnknownHostException exception) {
            return Optional.empty();
        }
    }

    private static boolean isLiteral(String value) {
        return value.indexOf(':') >= 0 ? isIpv6(value) : isIpv4(value);
    }

    /** 점으로 나뉜 네 칸. 앞자리 0 은 8진수로 읽힐 여지가 있어 받지 않는다. */
    private static boolean isIpv4(String value) {
        int start = 0;
        for (int part = 0; part < 4; part++) {
            int dot = value.indexOf('.', start);
            boolean last = part == 3;
            if (last == (dot >= 0)) {
                return false;
            }
            int end = last ? value.length() : dot;
            int length = end - start;
            if (length < 1 || length > 3) {
                return false;
            }
            if (length > 1 && value.charAt(start) == '0') {
                return false;
            }
            int number = 0;
            for (int index = start; index < end; index++) {
                char character = value.charAt(index);
                if (character < '0' || character > '9') {
                    return false;
                }
                number = number * 10 + (character - '0');
            }
            if (number > 255) {
                return false;
            }
            start = end + 1;
        }
        return true;
    }

    /**
     * 콜론으로 나뉜 여덟 칸. {@code ::} 로 줄인 형태와 끝에 IPv4 를 끼운 형태까지 받는다.
     * 구역 식별자({@code %eth0})는 요청 제한 키로 쓸 값이 아니므로 받지 않는다.
     */
    private static boolean isIpv6(String value) {
        if (value.indexOf('%') >= 0) {
            return false;
        }
        int shortened = value.indexOf("::");
        if (shortened >= 0 && value.indexOf("::", shortened + 1) >= 0) {
            return false;
        }
        if (value.startsWith(":") && !value.startsWith("::")) {
            return false;
        }
        if (value.endsWith(":") && !value.endsWith("::")) {
            return false;
        }

        String[] groups = value.split(":", -1);
        int filled = 0;
        boolean hasEmptyGroup = false;
        for (int index = 0; index < groups.length; index++) {
            String group = groups[index];
            if (group.isEmpty()) {
                hasEmptyGroup = true;
                continue;
            }
            if (group.indexOf('.') >= 0) {
                // 끼워 넣은 IPv4 는 맨 끝에만 올 수 있고 두 칸을 차지한다.
                if (index != groups.length - 1 || !isIpv4(group)) {
                    return false;
                }
                filled += 2;
                continue;
            }
            if (group.length() > 4) {
                return false;
            }
            for (int position = 0; position < group.length(); position++) {
                if (Character.digit(group.charAt(position), 16) < 0) {
                    return false;
                }
            }
            filled++;
        }

        return shortened >= 0
                ? hasEmptyGroup && filled < 8
                : !hasEmptyGroup && filled == 8;
    }
}
