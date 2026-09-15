package com.example.travlediary.seo;

import org.jsoup.Jsoup;

public final class SeoTextUtils {

    private static final int DESCRIPTION_LIMIT = 160;

    private SeoTextUtils() {
    }

    public static String summary(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String text = Jsoup.parse(html).text().replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return null;
        }
        int codePointCount = text.codePointCount(0, text.length());
        if (codePointCount <= DESCRIPTION_LIMIT) {
            return text;
        }
        int end = text.offsetByCodePoints(0, DESCRIPTION_LIMIT - 1);
        return text.substring(0, end).stripTrailing() + "…";
    }

    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
