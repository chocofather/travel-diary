package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NoticeUiContractTest {

    @Test
    void publicPagesUseMainLayoutBoardDetailAndSafeRichText() throws IOException {
        String list = file("src/main/resources/templates/support/notices/list.html");
        String detail = file("src/main/resources/templates/support/notices/detail.html");

        assertThat(list)
                .contains("layout/main")
                .contains("/css/support-layout.css")
                .contains("fragments/support/navigation :: navigation('notices')")
                .contains("support-notice-pin")
                .contains("class=\"support-notice-pin\">공지</span>")
                .contains("th:if=\"${notice.pinned}\"")
                .contains("class=\"support-notice-pin-icon\"")
                .contains("th:src=\"@{/images/pin.svg}\"")
                .contains("alt=\"\"", "aria-hidden=\"true\"")
                .contains("notice.title", "notice.createdAt", "notice.views")
                .contains("/support/notices/{id}")
                .contains("support-notice-pagination");
        assertThat(detail)
                .contains("layout/main")
                .contains("/css/support-layout.css")
                .contains("fragments/support/navigation :: navigation('notices')")
                .contains("quill-content.css")
                .contains("rich-text-content")
                .contains("th:utext=\"${notice.content}\"")
                .contains("목록으로")
                .doesNotContain("notice.userId");
    }

    @Test
    void adminFormReusesSharedQuillAndDoesNotAcceptUserId() throws IOException {
        String form = file("src/main/resources/templates/admin/notices/form.html");
        String script = file("src/main/resources/static/js/admin-notice-form.js");

        assertThat(form)
                .contains("quill@2.0.3")
                .contains("quill-resize-module@2.1.3")
                .contains("/js/quill-editor-init.js")
                .contains("/js/admin-notice-form.js")
                .contains("th:field=\"*{pinned}\"")
                .doesNotContain("name=\"userId\"", "th:field=\"*{userId}\"");
        assertThat(script)
                .contains("window.initQuillEditor")
                .contains("'#notice-editor'", "'notice-content'", "'notice-form'");
    }

    @Test
    void headerAndAdminSidebarPreserveNoticeLinksAlongsideCustomerSupportFeatures() throws IOException {
        String header = file("src/main/resources/templates/fragments/header.html");
        String sidebar = file("src/main/resources/templates/fragments/admin/sidebar.html");

        assertThat(header)
                .contains(">고객센터</a>")
                .contains("href=\"/support/notices\">공지사항</a>")
                .contains("href=\"/support/faq\">자주 묻는 질문</a>")
                .contains("href=\"/support/inquiries\">1:1 문의</a>")
                .contains("hasRole(''ADMIN'')")
                .contains("href=\"/admin\" class=\"profile-menu-item profile-menu-admin\"")
                .doesNotContain("class=\"admin-link\"");
        assertThat(sidebar)
                .contains("고객지원", "@{/admin/notices}", "activeMenu == 'notices'")
                .contains("@{/admin/faqs}", "activeMenu == 'faqs'")
                .contains("@{/admin/inquiries}", "activeMenu == 'inquiries'");
    }

    @Test
    void userAndAdminLogoutControlsUsePostFormsWithServerCsrfSupport() throws IOException {
        String header = file("src/main/resources/templates/fragments/header.html");
        String adminLayout = file("src/main/resources/templates/layout/admin.html");
        String login = file("src/main/resources/templates/login.html");

        assertThat(header)
                .contains("th:action=\"@{/logout}\"")
                .contains("method=\"post\"")
                .contains("name=\"redirect\" th:value=\"${currentUri}\"")
                .doesNotContain("href=\"/logout", "@{'/logout?");
        assertThat(adminLayout)
                .contains("th:action=\"@{/logout}\"")
                .contains("method=\"post\"")
                .doesNotContain("name=\"redirect\"", "href=\"/logout", "@{'/logout?");
        assertThat(occurrences(login, "name=\"redirect\"")).isEqualTo(1);
    }

    private String file(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private int occurrences(String source, String value) {
        return (source.length() - source.replace(value, "").length()) / value.length();
    }
}
