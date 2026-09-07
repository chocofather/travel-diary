package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FaqUiContractTest {

    @Test
    void publicFaqUsesNativeAccordionAndEscapedPlainText() throws IOException {
        String template = file("src/main/resources/templates/support/faq.html");
        String css = file("src/main/resources/static/css/support-faq.css");

        assertThat(template)
                .contains("layout/main")
                .contains("/css/support-layout.css")
                .contains("fragments/support/navigation :: navigation('faq')")
                .contains("<details", "<summary>")
                .contains("faq.categoryName", "faq.question")
                // 뱃지 클래스는 서버가 정해 준 값만 쓴다. 표시 이름은 언어에 따라 바뀐다.
                .contains("th:classappend=\"${faq.categoryBadge}\"")
                .contains("th:text=\"${faq.answer}\"")
                // 고정 문구는 메시지 번들에서 온다
                .contains("#{support.faq.title}", "#{support.faq.description}",
                        "#{support.faq.empty}")
                .doesNotContain("th:utext", "<script", "support-faq-pagination")
                // 한국어 이름 문자열을 스타일 판단(identity)에 쓰지 않는다
                .doesNotContain("categoryName == '", "is-account", "is-travel", "is-community",
                        "is-service", "is-etc", "is-default");
        assertThat(css)
                .contains("white-space: pre-wrap")
                .contains("summary:focus-visible")
                .contains(".support-faq-item[open]")
                .contains(".support-faq-category.is-account")
                .contains(".support-faq-category.is-travel")
                .contains(".support-faq-category.is-community")
                .contains(".support-faq-category.is-service")
                .contains(".support-faq-category.is-etc")
                .contains(".support-faq-category.is-default");
    }

    @Test
    void adminFormUsesCategorySelectPlainTextareaAndNoUserIdOrQuill() throws IOException {
        String form = file("src/main/resources/templates/admin/faqs/form.html");
        String list = file("src/main/resources/templates/admin/faqs/list.html");

        assertThat(form)
                .contains("th:each=\"category : ${categories}\"")
                .contains("th:field=\"*{categoryId}\"")
                .contains("th:field=\"*{question}\"")
                .contains("<textarea", "th:field=\"*{answer}\"")
                .contains("th:field=\"*{orderIndex}\"")
                .contains("th:field=\"*{visible}\"")
                .doesNotContain("Quill", "quill", "userId");
        assertThat(list)
                .contains("faq.categoryName", "faq.question", "faq.visible", "faq.orderIndex")
                .contains("method=\"post\"")
                .contains("/admin/faqs/{id}/delete");
    }

    /** 번역은 선택 입력이라 접힌 채로 원문 뒤에 오고, 답변은 편집기 없이 일반 textarea 를 쓴다. */
    @Test
    void adminFormAddsCollapsedTranslationTabsWithPlainTextAnswers() throws IOException {
        String form = file("src/main/resources/templates/admin/faqs/form.html");
        String fragment =
                file("src/main/resources/templates/fragments/admin/faq-translation-tabs.html");

        assertThat(form)
                .contains("/css/admin-translation-tabs.css")
                .contains("/js/admin-translation-tabs.js")
                .contains("/js/admin-translation-collapse.js")
                .contains("data-translation-collapsible")
                .contains("fragments/admin/faq-translation-tabs")
                .contains(":: faqTranslations")
                // 번역 본문 편집기는 쓰지 않는다
                .doesNotContain("/js/admin-translation-editors.js");
        // 한국어 원문(질문·답변) 다음에 번역이 온다
        assertThat(form.indexOf(":: faqTranslations"))
                .isGreaterThan(form.indexOf("th:field=\"*{answer}\""));
        assertThat(fragment)
                .contains("data-translation-tabs")
                .contains("data-translation-tabs-heading")
                .contains("data-translation-tabs-body")
                .contains("th:field=\"*{translations[__${slot.index}__].languageCode}\"")
                .contains("th:field=\"*{translations[__${slot.index}__].question}\"")
                .contains("th:field=\"*{translations[__${slot.index}__].answer}\"")
                // 답변은 일반 textarea 다 (Quill 편집기가 아니다)
                .contains("<textarea")
                .doesNotContain("quill", "data-translation-editor")
                // 0번 슬롯(한국어)은 탭에도 패널에도 그리지 않는다
                .contains("th:unless=\"${slot.first}\"")
                .doesNotContain("translations[0]")
                // 감출 때 입력을 비활성화하지 않는다 (disabled 면 저장에서 빠진다)
                .doesNotContain("disabled");
    }

    /** FAQ 카테고리 관리 화면은 기존 관리자 목록/폼 패턴과 공통 번역 자산을 그대로 쓴다. */
    @Test
    void faqCategoryAdminScreensReuseTheExistingAdminPatterns() throws IOException {
        String faqList = file("src/main/resources/templates/admin/faqs/list.html");
        String list = file("src/main/resources/templates/admin/faq-categories/list.html");
        String form = file("src/main/resources/templates/admin/faq-categories/form.html");
        String fragment = file(
                "src/main/resources/templates/fragments/admin/faq-category-translation-tabs.html");

        // FAQ 관리 화면에서 카테고리 관리로 들어갈 수 있다 (사이드바는 그대로 'faqs')
        assertThat(faqList).contains("@{/admin/faq-categories}", "카테고리 관리");
        assertThat(list)
                .contains("layout(~{::body}, ~{::headFragment}, 'faqs')")
                .contains("/css/admin-compact-lists.css")
                .contains("@{/admin/faq-categories/create}")
                .contains("@{/admin/faq-categories/edit/{id}(id=${category.id})}")
                .contains("@{/admin/faq-categories/{id}/delete(id=${category.id})}")
                .contains("method=\"post\"")
                .contains("category.faqCount")
                .contains("${error}");
        assertThat(form)
                .contains("layout(~{::body}, ~{::headFragment}, 'faqs')")
                .contains("/css/admin-translation-tabs.css")
                .contains("/js/admin-translation-tabs.js")
                .contains("/js/admin-translation-collapse.js")
                .contains("data-translation-collapsible")
                .contains("th:field=\"*{categoryName}\"")
                .contains("fragments/admin/faq-category-translation-tabs")
                .contains(":: faqCategoryTranslations")
                // 이름 한 칸뿐이라 편집기는 쓰지 않는다
                .doesNotContain("quill", "/js/admin-translation-editors.js");
        // 한국어 원문 다음에 번역이 온다
        assertThat(form.indexOf(":: faqCategoryTranslations"))
                .isGreaterThan(form.indexOf("th:field=\"*{categoryName}\""));
        assertThat(fragment)
                .contains("data-translation-tabs")
                .contains("data-translation-tabs-heading")
                .contains("data-translation-tabs-body")
                .contains("th:field=\"*{translations[__${slot.index}__].languageCode}\"")
                .contains("th:field=\"*{translations[__${slot.index}__].categoryName}\"")
                // 0번 슬롯(한국어)은 탭에도 패널에도 그리지 않는다
                .contains("th:unless=\"${slot.first}\"")
                .doesNotContain("translations[0]")
                // 감출 때 입력을 비활성화하지 않는다 (disabled 면 저장에서 빠진다)
                .doesNotContain("disabled");
    }

    @Test
    void headerAndSidebarPreserveFaqAlongsideImplementedInquiryLinks() throws IOException {
        String header = file("src/main/resources/templates/fragments/header.html");
        String sidebar = file("src/main/resources/templates/fragments/admin/sidebar.html");

        assertThat(header)
                .contains("href=\"/support/notices\" th:text=\"#{nav.support.notices}\"")
                .contains("href=\"/support/faq\" th:text=\"#{nav.support.faq}\"")
                .contains("href=\"/support/inquiries\" th:text=\"#{nav.support.inquiry}\"");
        assertThat(sidebar)
                .contains("@{/admin/notices}", "@{/admin/faqs}")
                .contains("activeMenu == 'faqs'")
                .contains("@{/admin/inquiries}", "activeMenu == 'inquiries'");
    }

    @Test
    void securityPublishesOnlyExactFaqGetAndProtectsExactAdminMutations() throws IOException {
        String security = file(
                "src/main/java/com/example/travlediary/config/SecurityConfig.java");

        assertThat(security)
                .contains("^/admin/faqs$")
                .contains("^/admin/faqs/[0-9]+/edit$")
                .contains("^/admin/faqs/[0-9]+/delete$")
                .contains("HttpMethod.GET, \"/support/faq\"")
                .contains("^/admin/notices$", "^/bookmarks/travel-info/[0-9]+$", "^/logout$")
                .doesNotContain("HttpMethod.GET, \"/support/**\"");
    }

    @Test
    void supportNavigationIsSharedAccessibleAndResponsive() throws IOException {
        String navigation = file(
                "src/main/resources/templates/fragments/support/navigation.html");
        String layoutCss = file("src/main/resources/static/css/support-layout.css");

        assertThat(navigation)
                // 메뉴 문구와 접근성 라벨은 메시지 번들에서 온다
                .contains("<nav", "aria-label=#{support.nav.label}")
                .contains("#{support.title}", "#{nav.support.notices}", "#{nav.support.faq}",
                        "#{nav.support.inquiry}")
                .contains("@{/support/notices}", "@{/support/faq}", "@{/support/inquiries}")
                .contains("aria-current=${activeMenu == 'notices'} ? 'page' : null")
                .contains("aria-current=${activeMenu == 'faq'} ? 'page' : null")
                .contains("aria-current=${activeMenu == 'inquiries'} ? 'page' : null");
        assertThat(layoutCss)
                .contains("grid-template-columns: 210px minmax(0, 1fr)")
                .contains("@media (max-width: 900px)")
                .contains("grid-template-columns: minmax(0, 1fr)")
                .contains("display: flex")
                .contains("overflow-x: auto")
                .contains(".support-navigation-link.is-active");
    }

    private String file(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
