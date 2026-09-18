package com.example.travlediary.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 전달 머리말을 믿어도 되는 접속 경로의 목록.
 *
 * <p>주소 하나({@code 10.0.0.5}) 또는 CIDR({@code 127.0.0.0/8})로 적는다. 특정 개발자나
 * 특정 서버의 주소를 코드에 박지 않고 설정으로만 받는다 — Cloudflare Tunnel 은 같은 기기의
 * loopback 으로 들어오고, 나중에 Nginx/VPS 로 옮기면 그 프록시의 주소를 적으면 된다.
 *
 * <p>목록이 비어 있으면 어떤 경로도 믿지 않는다. 설정을 잘못 적어 목록이 비는 쪽이,
 * 잘못 적은 값을 넓게 믿는 쪽보다 안전하다.
 */
final class TrustedProxies {

    private final List<Range> ranges;

    private TrustedProxies(List<Range> ranges) {
        this.ranges = List.copyOf(ranges);
    }

    /**
     * @param definitions 쉼표로 이어 적은 주소/CIDR 목록. 읽을 수 없는 값은 조용히 버린다.
     */
    static TrustedProxies parse(String definitions) {
        List<Range> parsed = new ArrayList<>();
        if (definitions != null) {
            for (String definition : definitions.split(",")) {
                Range.parse(definition.strip()).ifPresent(parsed::add);
            }
        }
        return new TrustedProxies(parsed);
    }

    boolean isEmpty() {
        return ranges.isEmpty();
    }

    /** 그 주소에서 온 요청의 전달 머리말을 믿어도 되는지. */
    boolean contains(String ipAddress) {
        return IpAddresses.toBytes(ipAddress)
                .map(address -> ranges.stream().anyMatch(range -> range.contains(address)))
                .orElse(false);
    }

    /** 주소 하나 또는 CIDR 한 구간. IPv4 와 IPv6 는 서로 섞이지 않는다. */
    private record Range(byte[] prefix, int bits) {

        static Optional<Range> parse(String definition) {
            if (definition == null || definition.isEmpty()) {
                return Optional.empty();
            }
            int slash = definition.indexOf('/');
            String address = slash < 0 ? definition : definition.substring(0, slash);
            Optional<byte[]> bytes = IpAddresses.toBytes(address);
            if (bytes.isEmpty()) {
                return Optional.empty();
            }
            int maxBits = bytes.get().length * 8;
            if (slash < 0) {
                return Optional.of(new Range(bytes.get(), maxBits));
            }
            final int bits;
            try {
                bits = Integer.parseInt(definition.substring(slash + 1).strip());
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
            if (bits < 0 || bits > maxBits) {
                return Optional.empty();
            }
            return Optional.of(new Range(bytes.get(), bits));
        }

        boolean contains(byte[] address) {
            if (address.length != prefix.length) {
                return false;
            }
            int wholeBytes = bits / 8;
            for (int index = 0; index < wholeBytes; index++) {
                if (address[index] != prefix[index]) {
                    return false;
                }
            }
            int remainingBits = bits % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xff << (8 - remainingBits);
            return (address[wholeBytes] & mask) == (prefix[wholeBytes] & mask);
        }
    }
}
