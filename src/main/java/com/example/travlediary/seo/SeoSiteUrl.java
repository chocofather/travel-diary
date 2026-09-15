package com.example.travlediary.seo;

import jakarta.servlet.http.HttpServletRequest;

public final class SeoSiteUrl {

    private SeoSiteUrl() {
    }

    public static String resolveBaseUrl(String configuredBaseUrl, HttpServletRequest request) {
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            return removeTrailingSlash(configuredBaseUrl.trim());
        }
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
    }

    public static String absolute(String baseUrl, String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            return null;
        }
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return pathOrUrl;
        }
        return removeTrailingSlash(baseUrl) + (pathOrUrl.startsWith("/") ? pathOrUrl : "/" + pathOrUrl);
    }

    private static String removeTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
