package com.tripbora.service.kto;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class KtoPhotoUrlValidator {

    private static final String ALLOWED_HOST = "tong.visitkorea.or.kr";
    private static final String WEBSITE_PATH_PREFIX = "/cms2/website/";
    private static final String FESTIVAL_RESOURCE_PATH_PREFIX = "/cms/resource/";
    /** Commons 원본은 upload, API가 주는 렌디션(thumburl)은 thumb 호스트에서 내려온다. */
    private static final Set<String> COMMONS_HOSTS = Set.of("upload.wikimedia.org", "thumb.wikimedia.org");
    private static final String COMMONS_PATH_PREFIX = "/wikipedia/commons/";

    private final HostResolver hostResolver;
    private final Set<String> allowedHosts;
    private final List<String> allowedPathPrefixes;
    private final boolean httpAllowed;

    public KtoPhotoUrlValidator() {
        this(InetAddress::getAllByName);
    }

    KtoPhotoUrlValidator(HostResolver hostResolver) {
        this(Set.of(ALLOWED_HOST), List.of(WEBSITE_PATH_PREFIX, FESTIVAL_RESOURCE_PATH_PREFIX), true, hostResolver);
    }

    private KtoPhotoUrlValidator(Set<String> allowedHosts, List<String> allowedPathPrefixes,
                                 boolean httpAllowed, HostResolver hostResolver) {
        this.allowedHosts = Set.copyOf(allowedHosts);
        this.allowedPathPrefixes = List.copyOf(allowedPathPrefixes);
        this.httpAllowed = httpAllowed;
        this.hostResolver = hostResolver;
    }

    /** 서버가 Commons API에서 다시 확인한 Wikimedia 파일 호스트의 HTTPS URL만 허용한다. */
    public static KtoPhotoUrlValidator wikimediaCommons() {
        return wikimediaCommons(InetAddress::getAllByName);
    }

    static KtoPhotoUrlValidator wikimediaCommons(HostResolver hostResolver) {
        return new KtoPhotoUrlValidator(COMMONS_HOSTS, List.of(COMMONS_PATH_PREFIX), false, hostResolver);
    }

    public URI validate(String imageUrl) {
        URI uri = parse(imageUrl);
        String scheme = normalizedScheme(uri);

        if (!((httpAllowed && "http".equals(scheme)) || "https".equals(scheme))
                || uri.getHost() == null
                || !allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT))
                || uri.getUserInfo() != null
                || uri.getFragment() != null
                || hasNonStandardPort(uri, scheme)
                || !hasAllowedPath(uri)) {
            throw new InvalidKtoPhotoUrlException();
        }

        verifyPublicAddresses(uri.getHost());
        return uri;
    }

    private URI parse(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new InvalidKtoPhotoUrlException();
        }
        try {
            return new URI(imageUrl.strip());
        } catch (URISyntaxException exception) {
            throw new InvalidKtoPhotoUrlException();
        }
    }

    private String normalizedScheme(URI uri) {
        return uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    }

    private boolean hasNonStandardPort(URI uri, String scheme) {
        int port = uri.getPort();
        return port != -1
                && !("http".equals(scheme) && port == 80)
                && !("https".equals(scheme) && port == 443);
    }

    private boolean hasAllowedPath(URI uri) {
        String path = uri.getPath();
        if (!hasAllowedPathPrefix(path) || !hasAllowedPathPrefix(uri.getRawPath())) {
            return false;
        }
        for (String segment : path.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return uri.normalize().getPath().equals(path);
    }

    private boolean hasAllowedPathPrefix(String path) {
        return path != null && allowedPathPrefixes.stream().anyMatch(path::startsWith);
    }

    private void verifyPublicAddresses(String host) {
        InetAddress[] addresses;
        try {
            addresses = hostResolver.resolve(host);
        } catch (UnknownHostException exception) {
            throw new InvalidKtoPhotoUrlException();
        }
        if (addresses == null || addresses.length == 0) {
            throw new InvalidKtoPhotoUrlException();
        }
        for (InetAddress address : addresses) {
            if (address == null || isUnsafe(address)) {
                throw new InvalidKtoPhotoUrlException();
            }
        }
    }

    private boolean isUnsafe(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress()
                || isIpv6UniqueLocal(address);
    }

    private boolean isIpv6UniqueLocal(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        byte first = address.getAddress()[0];
        return (first & 0xfe) == 0xfc;
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
