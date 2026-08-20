package com.example.travlediary.service.kto;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

public class KtoPhotoUrlValidator {

    private static final String ALLOWED_HOST = "tong.visitkorea.or.kr";
    private static final String ALLOWED_PATH_PREFIX = "/cms2/website/";

    private final HostResolver hostResolver;

    public KtoPhotoUrlValidator() {
        this(InetAddress::getAllByName);
    }

    KtoPhotoUrlValidator(HostResolver hostResolver) {
        this.hostResolver = hostResolver;
    }

    public URI validate(String imageUrl) {
        URI uri = parse(imageUrl);
        String scheme = normalizedScheme(uri);

        if (!("http".equals(scheme) || "https".equals(scheme))
                || uri.getHost() == null
                || !ALLOWED_HOST.equalsIgnoreCase(uri.getHost())
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
        if (path == null || !path.startsWith(ALLOWED_PATH_PREFIX)) {
            return false;
        }
        for (String segment : path.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return uri.normalize().getPath().equals(path);
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
