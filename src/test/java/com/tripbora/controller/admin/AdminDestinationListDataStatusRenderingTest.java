package com.tripbora.controller.admin;

import com.tripbora.dto.AdminDestinationListItemDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 여행지 목록의 데이터 상태 표시. 실제 list.html 본문을 렌더한다.
 * 문제가 있는 행에만 작은 상태 표시가 붙고, 바로가기는 지금 데이터 상태를 활성으로 보여 준다.
 */
class AdminDestinationListDataStatusRenderingTest {

    @Test
    void onlyRowsWithMissingDataGetBadgesAndTheActiveShortcutIsMarked() throws IOException {
        AdminDestinationListItemDto complete = row(1L, "경복궁");
        complete.setCompleteTranslationLanguages("en,ja,zh-CN,zh-TW");
        AdminDestinationListItemDto incomplete = row(2L, "남산공원");
        incomplete.setMissingImage(true);
        incomplete.setCompleteTranslationLanguages("en,zh-CN");
        AdminDestinationListItemDto untranslated = row(3L, "한옥호텔");
        untranslated.setMissingCategory(true);

        Document page = render(List.of(complete, incomplete, untranslated), "missing_image");

        List<Element> rows = page.select("tbody tr");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).select(".admin-data-issue")).isEmpty();
        assertThat(rows.get(1).select(".admin-data-issue")).extracting(Element::text)
                .containsExactly("이미지 없음", "번역 누락: ja, zh-TW");
        assertThat(rows.get(2).select(".admin-data-issue")).extracting(Element::text)
                .containsExactly("카테고리 없음", "번역 없음");

        List<Element> shortcuts = page.select(".admin-data-status-link");
        assertThat(shortcuts).extracting(Element::text)
                .containsExactly("전체 1,284", "이미지 없음 12", "대표 이미지 없음 4", "카테고리 없음 0",
                        "번역 미완료 19", "기본 설명 없음 7");
        assertThat(shortcuts.get(1).hasClass("is-active")).isTrue();
        assertThat(shortcuts.get(1).attr("aria-current")).isEqualTo("true");
        assertThat(shortcuts.get(0).hasClass("is-active")).isFalse();
        assertThat(shortcuts.get(2).hasClass("has-issue")).isTrue();
        assertThat(shortcuts.get(3).hasClass("has-issue")).isFalse();
        assertThat(shortcuts.get(1).attr("href")).isEqualTo("/admin/destinations?dataStatus=missing_image");
        assertThat(page.text()).doesNotContain("문제 있음");

        assertThat(page.select("#destination-data-status-filter option[selected]").attr("value"))
                .isEqualTo("missing_image");
    }

    private static AdminDestinationListItemDto row(Long id, String name) {
        AdminDestinationListItemDto row = new AdminDestinationListItemDto();
        row.setId(id);
        row.setName(name);
        row.setRegionName("종로구");
        return row;
    }

    private Document render(List<AdminDestinationListItemDto> rows, String dataStatus) throws IOException {
        MockServletContext servletContext = new MockServletContext();
        WebContext context = new WebContext(JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(new MockHttpServletRequest(servletContext), new MockHttpServletResponse()),
                Locale.KOREAN);
        Map<String, String> statusLabels = new LinkedHashMap<>();
        statusLabels.put("missing_image", "이미지 없음");
        statusLabels.put("missing_main_image", "대표 이미지 없음");
        context.setVariable("destinationList", rows);
        context.setVariable("dataStatus", dataStatus);
        context.setVariable("dataStatusLabels", statusLabels);
        context.setVariable("destinationTypeLabels", Map.of());
        context.setVariable("sortLabels", Map.of("latest", "최신 등록순"));
        context.setVariable("sort", "latest");
        context.setVariable("statusShortcuts", List.of(
                new AdminDestinationController.DataStatusShortcut(null, "전체", 1284, "/admin/destinations"),
                new AdminDestinationController.DataStatusShortcut("missing_image", "이미지 없음", 12,
                        "/admin/destinations?dataStatus=missing_image"),
                new AdminDestinationController.DataStatusShortcut("missing_main_image", "대표 이미지 없음", 4,
                        "/admin/destinations?dataStatus=missing_main_image"),
                new AdminDestinationController.DataStatusShortcut("missing_category", "카테고리 없음", 0,
                        "/admin/destinations?dataStatus=missing_category"),
                new AdminDestinationController.DataStatusShortcut("missing_translation", "번역 미완료", 19,
                        "/admin/destinations?dataStatus=missing_translation"),
                new AdminDestinationController.DataStatusShortcut("missing_description", "기본 설명 없음", 7,
                        "/admin/destinations?dataStatus=missing_description")));
        context.setVariable("totalCount", rows.size());
        context.setVariable("pageOffset", 0L);
        context.setVariable("currentPage", 1);
        context.setVariable("totalPages", 1);
        context.setVariable("listUrl", "/admin/destinations?dataStatus=" + dataStatus);
        context.setVariable("listQuery", "?dataStatus=" + dataStatus);
        return Jsoup.parse(engine().process(listBody(), context));
    }

    /** list.html 의 본문 fragment(레이아웃 제외). */
    private String listBody() throws IOException {
        String list;
        try (InputStream input = getClass().getResourceAsStream("/templates/admin/destinations/list.html")) {
            assertThat(input).isNotNull();
            list = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        int start = list.indexOf("<th:block th:fragment=\"body\">");
        int end = list.lastIndexOf("</th:block>");
        assertThat(start).isNotNegative();
        return list.substring(start, end + "</th:block>".length());
    }

    private static SpringTemplateEngine engine() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
