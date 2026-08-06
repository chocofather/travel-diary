package com.example.travlediary.service.post;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Component
public class PostContentSanitizer {

    private final Safelist safelist = Safelist.relaxed()
            .addTags("del", "figure", "figcaption")
            .addAttributes("img", "class")
            .removeProtocols("img", "src", "http", "https");

    public String sanitize(String content) {
        String nonNullContent = content == null ? "" : content;
        String cleaned = Jsoup.clean(nonNullContent, "", safelist, outputSettings());
        Document document = Jsoup.parseBodyFragment(cleaned);

        for (Element image : document.select("img[src]")) {
            String src = image.attr("src").trim();
            if (!isSafeImageSource(src)) {
                image.removeAttr("src");
            }
        }

        return document.body().html();
    }

    private Document.OutputSettings outputSettings() {
        return new Document.OutputSettings().prettyPrint(false);
    }

    private boolean isSafeImageSource(String src) {
        if (src.isEmpty() || src.indexOf('\\') >= 0 || src.chars().anyMatch(Character::isISOControl)) {
            return false;
        }

        String lower = src.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("//")) {
            return false;
        }

        try {
            URI uri = new URI(src);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return uri.getRawAuthority() == null;
            }
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
