package com.example.travlediary.service.post;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PostContentSanitizer {

    private static final Set<String> INLINE_FORMAT_TAGS = Set.of(
            "span", "strong", "em", "u", "s", "a"
    );
    private static final Set<String> BLOCK_FORMAT_TAGS = Set.of(
            "p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "li"
    );
    private static final Set<String> INLINE_QUILL_CLASSES = Set.of(
            "ql-font-serif", "ql-font-monospace", "ql-font-pretendard",
            "ql-font-noto-sans-kr", "ql-font-noto-serif-kr",
            "ql-font-nanum-human", "ql-font-school-safe-bareonbatang",
            "ql-font-cafe24-dongdong", "ql-font-gangwon-saeeum",
            "ql-size-small", "ql-size-large", "ql-size-huge"
    );
    private static final Set<String> BLOCK_QUILL_CLASSES = Set.of(
            "ql-align-center", "ql-align-right", "ql-align-justify",
            "ql-indent-1", "ql-indent-2", "ql-indent-3", "ql-indent-4",
            "ql-indent-5", "ql-indent-6", "ql-indent-7", "ql-indent-8"
    );
    private static final Set<String> CHECKLIST_STATES = Set.of("checked", "unchecked");
    private static final Set<String> ALLOWED_COLOR_PROPERTIES = Set.of("color", "background-color");
    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?$");
    private static final Pattern RGB_COLOR = Pattern.compile(
            "(?i)^rgb\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*\\)$"
    );
    private static final Pattern TEL_LINK = Pattern.compile("(?i)^tel:[+0-9(). -]+$");
    private static final Pattern IMAGE_WIDTH = Pattern.compile("^[1-9]\\d{0,3}$");
    private static final int MIN_IMAGE_WIDTH = 120;
    private static final int MAX_IMAGE_WIDTH = 1200;

    private final Safelist safelist = createSafelist();

    public String sanitize(String content) {
        String nonNullContent = content == null ? "" : content;
        String cleaned = Jsoup.clean(nonNullContent, "", safelist, outputSettings());
        Document document = Jsoup.parseBodyFragment(cleaned);

        sanitizeQuillClasses(document);
        sanitizeQuillStyles(document);
        sanitizeChecklistStates(document);

        for (Element link : document.select("a[href^=tel:]")) {
            if (!TEL_LINK.matcher(link.attr("href").trim()).matches()) {
                link.removeAttr("href");
            }
        }

        for (Element image : document.select("img")) {
            if (image.hasAttr("src")) {
                String src = image.attr("src").trim();
                if (!isSafeImageSource(src)) {
                    image.removeAttr("src");
                }
            }
            sanitizeImageWidth(image);
        }

        return document.body().html();
    }

    private static Safelist createSafelist() {
        Safelist safelist = Safelist.relaxed()
                .addTags("del", "s", "figure", "figcaption")
                .addAttributes("img", "class", "width")
                .addAttributes("li", "data-list")
                .addProtocols("a", "href", "tel")
                .removeProtocols("img", "src", "http", "https");

        for (String tag : INLINE_FORMAT_TAGS) {
            safelist.addAttributes(tag, "class", "style");
        }
        for (String tag : BLOCK_FORMAT_TAGS) {
            safelist.addAttributes(tag, "class");
        }
        return safelist;
    }

    private void sanitizeQuillClasses(Document document) {
        for (Element element : document.select("[class]")) {
            String tagName = element.tagName();
            Set<String> allowedNames = INLINE_FORMAT_TAGS.contains(tagName)
                    ? INLINE_QUILL_CLASSES
                    : BLOCK_FORMAT_TAGS.contains(tagName) ? BLOCK_QUILL_CLASSES : Set.of();

            StringJoiner safeClasses = new StringJoiner(" ");
            for (String className : element.className().trim().split("\\s+")) {
                if (allowedNames.contains(className)) {
                    safeClasses.add(className);
                }
            }

            String result = safeClasses.toString();
            if (result.isEmpty()) {
                element.removeAttr("class");
            } else {
                element.attr("class", result);
            }
        }
    }

    private void sanitizeChecklistStates(Document document) {
        for (Element item : document.select("li[data-list]")) {
            if (!CHECKLIST_STATES.contains(item.attr("data-list"))) {
                item.removeAttr("data-list");
            }
        }
    }

    private void sanitizeQuillStyles(Document document) {
        for (Element element : document.select("[style]")) {
            Map<String, String> safeDeclarations = new LinkedHashMap<>();

            for (String declaration : element.attr("style").split(";")) {
                int separator = declaration.indexOf(':');
                if (separator < 0) {
                    continue;
                }

                String property = declaration.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                String value = declaration.substring(separator + 1).trim();
                if (ALLOWED_COLOR_PROPERTIES.contains(property) && isSafeColor(value)) {
                    safeDeclarations.put(property, value);
                }
            }

            if (safeDeclarations.isEmpty()) {
                element.removeAttr("style");
                continue;
            }

            StringJoiner safeStyle = new StringJoiner("; ");
            safeDeclarations.forEach((property, value) -> safeStyle.add(property + ": " + value));
            element.attr("style", safeStyle.toString());
        }
    }

    private boolean isSafeColor(String value) {
        if (HEX_COLOR.matcher(value).matches()) {
            return true;
        }

        Matcher rgb = RGB_COLOR.matcher(value);
        if (!rgb.matches()) {
            return false;
        }
        return Integer.parseInt(rgb.group(1)) <= 255
                && Integer.parseInt(rgb.group(2)) <= 255
                && Integer.parseInt(rgb.group(3)) <= 255;
    }

    private void sanitizeImageWidth(Element image) {
        if (!image.hasAttr("width")) {
            return;
        }

        String width = image.attr("width").trim();
        if (!IMAGE_WIDTH.matcher(width).matches()) {
            image.removeAttr("width");
            return;
        }

        int numericWidth = Integer.parseInt(width);
        if (numericWidth < MIN_IMAGE_WIDTH || numericWidth > MAX_IMAGE_WIDTH) {
            image.removeAttr("width");
            return;
        }
        image.attr("width", Integer.toString(numericWidth));
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
