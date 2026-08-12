package com.example.travlediary.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class InternalRedirectValidator {

    private InternalRedirectValidator() {
    }

    static String normalize(String redirect) {
        if (redirect == null || redirect.isBlank()) {
            return null;
        }

        String candidate = redirect.strip();
        String lowerCaseCandidate = candidate.toLowerCase(Locale.ROOT);
        if (!candidate.startsWith("/")
                || candidate.startsWith("//")
                || candidate.contains("\\")
                || lowerCaseCandidate.contains("%0d")
                || lowerCaseCandidate.contains("%0a")) {
            return null;
        }

        try {
            URI uri = new URI(candidate);
            String rawPath = uri.getRawPath();
            String lowerCasePath = rawPath == null ? "" : rawPath.toLowerCase(Locale.ROOT);
            if (uri.isAbsolute()
                    || uri.getRawAuthority() != null
                    || lowerCasePath.contains("%2f")
                    || lowerCasePath.contains("%5c")) {
                return null;
            }
            return candidate;
        } catch (URISyntaxException exception) {
            return null;
        }
    }
}
