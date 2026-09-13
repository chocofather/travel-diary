package com.example.travlediary.service.policy;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

/**
 * 정책 본문을 화면에 그대로 내보내기 전에 한 번 거른다.
 *
 * <p>본문은 운영이 DB 에 직접 넣는 값이라 서식 있는 HTML 일 수도, 줄바꿈만 있는 평문일 수도 있다.
 * 두 경우 모두 화면이 깨지지 않도록 여기에서 하나의 안전한 HTML 로 맞춘다.
 * 다른 사용자 작성 본문과 같이 Jsoup 로 거르고, script/style 같은 실행 가능한 요소는 남기지 않는다.
 */
final class PolicyContent {

    private PolicyContent() {
    }

    /**
     * @param raw policy_translations.content 원문
     * @return 화면에 th:utext 로 그대로 쓸 수 있는 HTML. 입력이 비어 있으면 null
     */
    static String toSafeHtml(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        Document cleaned = new Cleaner(Safelist.relaxed())
                .clean(Jsoup.parseBodyFragment(raw));
        // 기본 pretty print 는 줄바꿈을 공백으로 바꿔 버린다. 원문 그대로 뽑는다.
        cleaned.outputSettings().prettyPrint(false);
        String safe = cleaned.body().html();

        // 걸러낸 뒤에도 요소가 하나도 없으면 줄바꿈이 유일한 서식이다. HTML 에서는 사라지므로 살려 준다.
        // 이 시점의 문자열은 이미 &lt; 형태로 이스케이프되어 있어 태그가 새로 생기지 않는다.
        return cleaned.body().children().isEmpty() ? safe.replace("\n", "<br>") : safe;
    }
}
