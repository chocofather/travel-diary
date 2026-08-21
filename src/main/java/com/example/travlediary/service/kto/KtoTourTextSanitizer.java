package com.example.travlediary.service.kto;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

final class KtoTourTextSanitizer {

    private KtoTourTextSanitizer() {
    }

    static String toPlainText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Document document = Jsoup.parseBodyFragment(value);
        document.select("script,style").remove();
        for (Element lineBreak : document.select("br")) {
            lineBreak.replaceWith(new TextNode("\n"));
        }
        String text = document.body().wholeText().replace('\u00a0', ' ');
        StringBuilder normalized = new StringBuilder();
        for (String line : text.split("\\R", -1)) {
            String stripped = line.strip().replaceAll("[ \\t]+", " ");
            if (stripped.isEmpty()) {
                continue;
            }
            if (!normalized.isEmpty()) {
                normalized.append('\n');
            }
            normalized.append(stripped);
        }
        return normalized.isEmpty() ? null : normalized.toString();
    }
}
