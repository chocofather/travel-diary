package com.example.travlediary.service.diary;

import com.example.travlediary.service.post.PostContentSanitizer;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 다이어리 본문(diary_pages.content) 전용 정리기.
 * 위험 요소 제거는 이미 검증된 {@link PostContentSanitizer} 를 그대로 거치고,
 * 여기서는 일기 작성에 필요한 서식만 남도록 범위를 좁힌다.
 */
@Component
@RequiredArgsConstructor
public class DiaryContentSanitizer {

    /** 본문에 남길 태그. 그 밖의 태그는 내용만 남기고 벗겨낸다. */
    private static final Set<String> ALLOWED_TAGS = Set.of(
            "p", "br", "span", "strong", "em", "b", "i", "u", "s", "del"
    );
    /**
     * 다이어리 툴바에서 고를 수 있는 글꼴 클래스.
     * (여행정보 등 다른 화면의 글꼴 허용 목록과 섞지 않는다)
     */
    public static final Set<String> DIARY_FONT_CLASSES = Set.of(
            "ql-font-fromsol", "ql-font-nanum-square", "ql-font-bookk-myeongjo",
            "ql-font-hiker", "ql-font-cafe24-surround", "ql-font-lee-seoyun",
            "ql-font-ggubulim", "ql-font-ohchungi", "ql-font-chosun-gungsuh",
            "ql-font-gunham", "ql-font-dunggeunmo", "ql-font-mitmi",
            "ql-font-green-umbrella", "ql-font-incheon-jaram", "ql-font-park-dahyun"
    );
    /**
     * 다이어리 툴바의 형광펜 색상. (diary-editor.js 의 HIGHLIGHTS 와 같은 값을 쓴다)
     * 배경색은 이 6종만 살리고, 그 밖의 값은 색 자체가 안전해도 버린다.
     */
    public static final Set<String> DIARY_HIGHLIGHT_COLORS = Set.of(
            "#fff5a5", "#ffd6e4", "#c9f2e3", "#cfe6fb", "#e2d9f7", "#ffdec2"
    );
    /** 툴바에서 쓰는 글꼴/글자 크기/정렬 클래스만 허용한다. */
    private static final Set<String> ALLOWED_CLASSES = allowedClasses();
    /** 글자색과 형광펜 배경색만 남긴다. (그 밖의 style 은 버린다) */
    private static final String COLOR_PROPERTY = "color";
    private static final String HIGHLIGHT_PROPERTY = "background-color";
    private static final Pattern HEX_COLOR =
            Pattern.compile("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");
    private static final Pattern RGB_COLOR = Pattern.compile(
            "(?i)^rgb\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*\\)$"
    );
    /**
     * 브라우저가 rgb() 로 돌려줘도 같은 색으로 알아보도록 정규화한 값으로 찾는다.
     * (위의 색상 패턴을 쓰므로 반드시 그 뒤에서 초기화한다)
     */
    private static final Map<String, String> HIGHLIGHTS_BY_RGB = highlightsByRgb();
    /** Jsoup 이 &nbsp; 를 돌려주는 문자 */
    private static final char NO_BREAK_SPACE = ' ';

    private final PostContentSanitizer postContentSanitizer;

    /**
     * 저장 가능한 본문 HTML 로 정리한다.
     * 내용이 사실상 비어 있으면 null 을 돌려준다. (빈 페이지는 content = NULL)
     */
    public String sanitize(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        // 위험 요소 제거는 공통 정리기에 맡기되, 다이어리 글꼴 클래스는 살아남게 함께 넘긴다.
        Document document = Jsoup.parseBodyFragment(
                postContentSanitizer.sanitize(content, DIARY_FONT_CLASSES));
        // body 자체는 대상이 아니므로 자식부터 훑는다.
        for (Element element : document.body().children().select("*")) {
            if (ALLOWED_TAGS.contains(element.tagName())) {
                keepAllowedClasses(element);
                keepAllowedStyles(element);
            } else {
                // 이미지/링크/제목 등 일기 본문에서 쓰지 않는 태그는 글자만 남긴다.
                element.unwrap();
            }
        }

        String html = document.body().html();
        return isEmpty(document.body().text()) ? null : html;
    }

    /** 빈 편집기가 만드는 &lt;p&gt;&lt;br&gt;&lt;/p&gt; 같은 값은 빈 본문으로 본다. */
    private boolean isEmpty(String text) {
        return text.replace(NO_BREAK_SPACE, ' ').isBlank();
    }

    /** 글꼴 + 글자 크기 + 정렬 클래스. 그 밖의 클래스는 모두 지운다. */
    private static Set<String> allowedClasses() {
        Set<String> allowed = new HashSet<>(DIARY_FONT_CLASSES);
        allowed.addAll(Set.of(
                "ql-size-small", "ql-size-large", "ql-size-huge",
                "ql-align-center", "ql-align-right", "ql-align-justify"
        ));
        return Set.copyOf(allowed);
    }

    /** 형광펜 색상을 "r,g,b" 로 펼쳐 두고, 어떤 표기로 저장돼도 같은 색을 찾을 수 있게 한다. */
    private static Map<String, String> highlightsByRgb() {
        Map<String, String> byRgb = new LinkedHashMap<>();
        for (String highlight : DIARY_HIGHLIGHT_COLORS) {
            byRgb.put(toRgbKey(highlight), highlight);
        }
        return Map.copyOf(byRgb);
    }

    /** #rgb / #rrggbb / rgb(r, g, b) 를 "r,g,b" 로 맞춘다. 알아볼 수 없으면 null. */
    private static String toRgbKey(String value) {
        Matcher hex = HEX_COLOR.matcher(value);
        if (hex.matches()) {
            String digits = hex.group(1);
            if (digits.length() == 3) {
                StringBuilder expanded = new StringBuilder();
                for (char digit : digits.toCharArray()) {
                    expanded.append(digit).append(digit);
                }
                digits = expanded.toString();
            }
            return rgbKey(
                    Integer.parseInt(digits.substring(0, 2), 16),
                    Integer.parseInt(digits.substring(2, 4), 16),
                    Integer.parseInt(digits.substring(4, 6), 16));
        }

        Matcher rgb = RGB_COLOR.matcher(value);
        if (!rgb.matches()) {
            return null;
        }
        int red = Integer.parseInt(rgb.group(1));
        int green = Integer.parseInt(rgb.group(2));
        int blue = Integer.parseInt(rgb.group(3));
        boolean inRange = red <= 255 && green <= 255 && blue <= 255;
        return inRange ? rgbKey(red, green, blue) : null;
    }

    private static String rgbKey(int red, int green, int blue) {
        return red + "," + green + "," + blue;
    }

    private void keepAllowedClasses(Element element) {
        if (!element.hasAttr("class")) {
            return;
        }

        StringJoiner safeClasses = new StringJoiner(" ");
        for (String className : element.className().trim().split("\\s+")) {
            if (ALLOWED_CLASSES.contains(className)) {
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

    private void keepAllowedStyles(Element element) {
        if (!element.hasAttr("style")) {
            return;
        }

        // PostContentSanitizer 를 거친 뒤라 값 자체는 이미 안전한 색상만 남아 있다.
        StringJoiner safeStyle = new StringJoiner("; ");
        for (String declaration : element.attr("style").split(";")) {
            int separator = declaration.indexOf(':');
            if (separator < 0) {
                continue;
            }
            String property = declaration.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = declaration.substring(separator + 1).trim();
            if (COLOR_PROPERTY.equals(property)) {
                safeStyle.add(property + ": " + value);
            } else if (HIGHLIGHT_PROPERTY.equals(property)) {
                // 형광펜은 툴바가 주는 6종만 남기고, 저장 값은 항상 같은 표기로 맞춘다.
                String rgbKey = toRgbKey(value);
                String highlight = rgbKey == null ? null : HIGHLIGHTS_BY_RGB.get(rgbKey);
                if (highlight != null) {
                    safeStyle.add(property + ": " + highlight);
                }
            }
        }

        String result = safeStyle.toString();
        if (result.isEmpty()) {
            element.removeAttr("style");
        } else {
            element.attr("style", result);
        }
    }
}
