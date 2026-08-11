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
                .contains("is-account", "is-travel", "is-community", "is-service", "is-etc", "is-default")
                .contains("th:text=\"${faq.answer}\"")
                .contains("등록된 자주 묻는 질문이 없습니다.")
                .doesNotContain("th:utext", "<script", "support-faq-pagination");
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

    @Test
    void headerAndSidebarExposeImplementedFaqWithoutInquiryLinks() throws IOException {
        String header = file("src/main/resources/templates/fragments/header.html");
        String sidebar = file("src/main/resources/templates/fragments/admin/sidebar.html");

        assertThat(header)
                .contains("href=\"/support/notices\">공지사항</a>")
                .contains("href=\"/support/faq\">자주 묻는 질문</a>")
                .contains("submenu-link-disabled", "1:1 문의")
                .doesNotContain("href=\"/support/inquiries");
        assertThat(sidebar)
                .contains("@{/admin/notices}", "@{/admin/faqs}")
                .contains("activeMenu == 'faqs'")
                .doesNotContain("@{/admin/inquiries}");
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
                .contains("<nav", "aria-label=\"고객센터 메뉴\"")
                .contains("@{/support/notices}", "@{/support/faq}")
                .contains("aria-current=${activeMenu == 'notices'} ? 'page' : null")
                .contains("aria-current=${activeMenu == 'faq'} ? 'page' : null")
                .contains("aria-disabled=\"true\">1:1 문의</span>")
                .doesNotContain("/support/inquiries");
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
