package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InquiryUiContractTest {

    @Test
    void userPagesReuseSupportNavigationTabsAndPlainTextRendering() throws IOException {
        String list = file("src/main/resources/templates/support/inquiries/list.html");
        String form = file("src/main/resources/templates/support/inquiries/form.html");
        String detail = file("src/main/resources/templates/support/inquiries/detail.html");
        String tabs = file("src/main/resources/templates/fragments/support/inquiry-tabs.html");

        assertThat(list)
                .contains("fragments/support/navigation :: navigation('inquiries')")
                .contains("fragments/support/inquiry-tabs :: tabs('list')")
                .contains("inquiry.status.displayName", "inquiry.inquiryType.displayName")
                .contains("support-inquiry-pagination")
                .contains("등록한 1:1 문의가 없습니다.");
        assertThat(form)
                .contains("fragments/support/inquiry-tabs :: tabs(${activeInquiryTab})")
                .contains("th:action=\"${formAction}\"")
                .contains("th:text=\"${submitLabel}\"")
                .contains("th:field=\"*{inquiryType}\"")
                .contains("th:field=\"*{subject}\"")
                .contains("th:field=\"*{content}\"")
                .contains("maxlength=\"5000\"")
                .doesNotContain("userId", "status", "Quill", "quill");
        assertThat(detail)
                .contains("fragments/support/inquiry-tabs :: tabs('list')")
                .contains("th:text=\"${inquiry.content}\"")
                .contains("th:text=\"${inquiry.answerContent}\"")
                .contains("th:if=\"${inquiry.pending}\"")
                .contains("/support/inquiries/{id}/edit")
                .contains("/support/inquiries/{id}/delete")
                .doesNotContain("th:utext", "Quill", "quill");
        assertThat(tabs)
                .contains("@{/support/inquiries/new}", "@{/support/inquiries}")
                .contains("aria-current=${activeTab == 'new'} ? 'page' : null")
                .contains("aria-current=${activeTab == 'list'} ? 'page' : null");
    }

    @Test
    void adminPagesProvideOnlyStatusFilterAndSingleAnswerMutation() throws IOException {
        String list = file("src/main/resources/templates/admin/inquiries/list.html");
        String detail = file("src/main/resources/templates/admin/inquiries/detail.html");

        assertThat(list)
                .contains("status='PENDING'", "status='ANSWERED'")
                .contains("currentStatus")
                .contains("inquiry.userDisplayName")
                .contains("admin-inquiry-pagination")
                .doesNotContain("검색");
        assertThat(detail)
                .contains("/admin/inquiries/{id}/answer")
                .contains("답변 등록", "답변 수정")
                .contains("th:text=\"${inquiry.content}\"")
                .contains("th:field=\"*{content}\"")
                .doesNotContain("th:utext", "/answer/delete", "userId");
    }

    @Test
    void stylesPreservePlainTextLineBreaksAndResponsiveLayout() throws IOException {
        String userCss = file("src/main/resources/static/css/support-inquiries.css");
        String adminCss = file("src/main/resources/static/css/admin-inquiries.css");

        assertThat(userCss)
                .contains("white-space: pre-wrap")
                .contains(".support-inquiry-status.is-pending")
                .contains(".support-inquiry-status.is-answered")
                .contains("@media (max-width: 720px)", "@media (max-width: 520px)");
        assertThat(adminCss)
                .contains("white-space: pre-wrap")
                .contains(".admin-inquiry-status.is-pending")
                .contains(".admin-inquiry-status.is-answered");
    }

    @Test
    void securityKeepsPublicSupportNarrowAndAddsExactInquiryCsrfMatchers() throws IOException {
        String security = file("src/main/java/com/example/travlediary/config/SecurityConfig.java");

        assertThat(security)
                .contains("HttpMethod.GET, \"/support/notices\"")
                .contains("HttpMethod.GET, \"/support/faq\"")
                .contains("\"/support/inquiries\", \"/support/inquiries/**\").authenticated()")
                .contains("^/support/inquiries$")
                .contains("^/support/inquiries/[0-9]+/edit$")
                .contains("^/support/inquiries/[0-9]+/delete$")
                .contains("^/admin/inquiries/[0-9]+/answer$")
                .contains("^/admin/notices$", "^/admin/faqs$", "^/logout$")
                .doesNotContain("\"/support/**\").permitAll()");
    }

    @Test
    void headerAndBothSidebarsLinkCanonicalInquiryRoutes() throws IOException {
        String header = file("src/main/resources/templates/fragments/header.html");
        String supportNavigation = file(
                "src/main/resources/templates/fragments/support/navigation.html");
        String adminSidebar = file(
                "src/main/resources/templates/fragments/admin/sidebar.html");

        assertThat(header).contains("href=\"/support/inquiries\">1:1 문의</a>");
        assertThat(supportNavigation)
                .contains("@{/support/inquiries}")
                .contains("activeMenu == 'inquiries'")
                .contains("aria-current=${activeMenu == 'inquiries'} ? 'page' : null");
        assertThat(adminSidebar)
                .contains("@{/admin/inquiries}")
                .contains("activeMenu == 'inquiries'");
    }

    private String file(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
