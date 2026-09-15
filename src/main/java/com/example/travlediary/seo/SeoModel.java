package com.example.travlediary.seo;

import org.springframework.ui.Model;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SeoModel {

    private SeoModel() {
    }

    public static void apply(Model model, String title, String description,
                             String canonicalPath, String imageUrl, String openGraphType) {
        putIfPresent(model, "seoTitle", title);
        putIfPresent(model, "seoDescription", SeoTextUtils.summary(description));
        putIfPresent(model, "seoCanonicalPath", canonicalPath);
        putIfPresent(model, "seoImage", imageUrl);
        putIfPresent(model, "seoOpenGraphType", openGraphType);
    }

    public static String canonicalPath(String path, Map<String, ?> parameters) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        if (parameters != null) {
            parameters.forEach((name, value) -> addQueryParameter(builder, name, value));
        }
        return builder.build().encode().toUriString();
    }

    public static Map<String, Object> parameters() {
        return new LinkedHashMap<>();
    }

    private static void addQueryParameter(UriComponentsBuilder builder, String name, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String string && string.isBlank()) {
            return;
        }
        if (value instanceof Number number && number.longValue() <= 1 && "page".equals(name)) {
            return;
        }
        if (value instanceof List<?> values) {
            values.forEach(item -> builder.queryParam(name, item));
            return;
        }
        builder.queryParam(name, value);
    }

    private static void putIfPresent(Model model, String name, String value) {
        if (value != null && !value.isBlank()) {
            model.addAttribute(name, value);
        }
    }
}
