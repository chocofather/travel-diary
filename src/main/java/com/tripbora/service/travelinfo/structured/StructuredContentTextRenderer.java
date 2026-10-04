package com.tripbora.service.travelinfo.structured;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 구조화 블록의 글을 모아 {@code travel_info.content} 에 넣을 단순한 HTML 을 만든다.
 *
 * <p>목적은 공개 디자인이 아니라 기존 통합검색(content LIKE)·SEO 요약·hasContent 가 STRUCTURED 글에서도
 * 그대로 동작하게 하는 것이다. 그래서 h2 / h3 / p / ul / li / br 만 쓰고 이미지 태그는 넣지 않는다.
 * 이미지 블록은 캡션(없으면 alt)을 글로 남긴다. 검사를 통과한 문서라면 블록마다 글이 하나 이상 남는다.
 *
 * <p>사용자 글은 모두 escape 한다. 또 {@code travel_info.content} 는 utf8mb3 라 4바이트 문자(이모지 등)를
 * 그대로 넣으면 저장이 실패하므로, 그런 문자는 HTML 숫자 참조({@code &#x1F600;})로 바꾼다.
 * 브라우저와 Jsoup 은 같은 문자로 읽는다. 단, 이 결과를 Jsoup 으로 다시 직렬화하면(예: Quill 용 sanitizer)
 * 숫자 참조가 원래 4바이트 문자로 풀려 저장이 실패한다. 그래서 저장 전에 sanitizer 를 다시 거치지 않는다.
 */
@Component
public class StructuredContentTextRenderer {

    private static final Pattern BLANK_LINES = Pattern.compile("\\n\\s*\\n");

    public String render(StructuredContent content) {
        StringBuilder html = new StringBuilder();
        for (StructuredBlock block : content.blocks()) {
            if (block instanceof StructuredBlock.SectionTitle sectionTitle) {
                element(html, "h2", sectionTitle.title());
            } else if (block instanceof StructuredBlock.RichText richText) {
                paragraphs(html, richText.text());
            } else if (block instanceof StructuredBlock.FullImage fullImage) {
                element(html, "p", firstText(fullImage.caption(), fullImage.alt()));
            } else if (block instanceof StructuredBlock.ImageText imageText) {
                element(html, "h3", imageText.title());
                paragraphs(html, imageText.text());
            } else if (block instanceof StructuredBlock.ImageSlider slider) {
                element(html, "h3", slider.title());
                imageItems(html, slider.items());
            } else if (block instanceof StructuredBlock.ImageGrid grid) {
                imageItems(html, grid.items());
            } else if (block instanceof StructuredBlock.Callout callout) {
                paragraphs(html, callout.text());
            } else {
                throw new IllegalArgumentException("지원하지 않는 블록입니다: " + block.type());
            }
        }
        return html.toString();
    }

    /** 슬라이더·이미지 배치의 이미지마다 제목과 캡션을 한 줄씩 둔다. 둘 다 없으면 alt 를 남긴다. */
    private void imageItems(StringBuilder html, List<StructuredBlock.SliderItem> imageItems) {
        StringBuilder items = new StringBuilder();
        for (StructuredBlock.SliderItem item : imageItems) {
            String title = hasText(item.title()) ? escapeHtml(item.title().strip()) : null;
            String caption = hasText(item.caption()) ? escapeHtml(item.caption().strip()) : null;
            if (title == null && caption == null) {
                title = hasText(item.alt()) ? escapeHtml(item.alt().strip()) : null;
            }
            if (title == null && caption == null) {
                continue;
            }
            items.append("<li>");
            if (title != null) {
                items.append(title);
            }
            if (title != null && caption != null) {
                items.append("<br>");
            }
            if (caption != null) {
                items.append(caption);
            }
            items.append("</li>");
        }
        if (!items.isEmpty()) {
            html.append("<ul>").append(items).append("</ul>");
        }
    }

    /** 한 줄 글. */
    private void element(StringBuilder html, String tag, String value) {
        if (!hasText(value)) {
            return;
        }
        html.append('<').append(tag).append('>')
                .append(escapeHtml(value.strip()))
                .append("</").append(tag).append('>');
    }

    /** 빈 줄로 나뉜 덩어리는 문단, 문단 안의 줄바꿈은 br 로 남긴다. */
    private void paragraphs(StringBuilder html, String value) {
        if (!hasText(value)) {
            return;
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        for (String paragraph : BLANK_LINES.split(normalized)) {
            String stripped = paragraph.strip();
            if (stripped.isEmpty()) {
                continue;
            }
            html.append("<p>");
            String[] lines = stripped.split("\n");
            for (int index = 0; index < lines.length; index++) {
                if (index > 0) {
                    html.append("<br>");
                }
                html.append(escapeHtml(lines[index].strip()));
            }
            html.append("</p>");
        }
    }

    private String firstText(String preferred, String fallback) {
        return hasText(preferred) ? preferred : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * HTML 특수 문자를 escape 하고, utf8mb3 컬럼에 들어가지 않는 4바이트 문자는 숫자 참조로 바꾼다.
     * 짝이 맞지 않는 surrogate 는 저장할 수 없으므로 대체 문자(U+FFFD)로 바꾼다. (검사를 거친 글에는 없다)
     */
    static String escapeHtml(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            switch (codePoint) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> {
                    if (Character.isSupplementaryCodePoint(codePoint)) {
                        escaped.append("&#x")
                                .append(Integer.toHexString(codePoint).toUpperCase(Locale.ROOT))
                                .append(';');
                    } else if (codePoint >= Character.MIN_SURROGATE
                            && codePoint <= Character.MAX_SURROGATE) {
                        escaped.append('�');
                    } else {
                        escaped.appendCodePoint(codePoint);
                    }
                }
            }
        });
        return escaped.toString();
    }
}
