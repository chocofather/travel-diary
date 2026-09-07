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
                // 고정 뱃지 문구도 메시지 번들에서 온다
                .contains("class=\"support-notice-pin\" th:text=\"#{support.notice.pin}\"")
                .contains("#{support.notice.title}", "#{support.notice.empty}")
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
                // 고정 문구는 메시지 번들에서 온다 (제목·본문은 DB 번역 값 그대로)
                .contains("#{support.notice.detail.breadcrumb}",
                        "#{support.notice.detail.createdAt}",
                        "#{support.notice.detail.views}",
                        "#{support.notice.detail.backToList}")
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

    /** 번역은 선택 입력이라 접힌 채로 원문 뒤에 오고, 탭·편집기·접기는 공용 자산을 그대로 쓴다. */
    @Test
    void adminFormAddsCollapsedTranslationTabsWithTheSharedAssets() throws IOException {
        String form = file("src/main/resources/templates/admin/notices/form.html");
        String fragment =
                file("src/main/resources/templates/fragments/admin/notice-translation-tabs.html");

        assertThat(form)
                .contains("/css/admin-translation-tabs.css")
                .contains("/js/admin-translation-tabs.js")
                .contains("/js/admin-translation-collapse.js")
                .contains("/js/admin-translation-editors.js")
                .contains("data-translation-collapsible")
                .contains("fragments/admin/notice-translation-tabs")
                .contains(":: noticeTranslations");
        // 한국어 원문(제목·본문) 다음에 번역이 온다
        assertThat(form.indexOf(":: noticeTranslations"))
                .isGreaterThan(form.indexOf("id=\"notice-content\""));
        assertThat(fragment)
                .contains("data-translation-tabs")
                .contains("data-translation-tabs-heading")
                .contains("data-translation-tabs-body")
                .contains("th:field=\"*{translations[__${slot.index}__].languageCode}\"")
                .contains("th:field=\"*{translations[__${slot.index}__].title}\"")
                .contains("th:field=\"*{translations[__${slot.index}__].content}\"")
                // 언어별 본문 편집기는 공용 규약(languageCode)으로 묶는다
                .contains("data-translation-editor=${translation.languageCode}")
                .contains("data-translation-content=${translation.languageCode}")
                .contains("data-translation-initial-content=${translation.languageCode}")
                // 0번 슬롯(한국어)은 탭에도 패널에도 그리지 않는다
                .contains("th:unless=\"${slot.first}\"")
                .doesNotContain("translations[0]")
                // 감출 때 입력을 비활성화하지 않는다 (disabled 면 저장에서 빠진다)
                .doesNotContain("disabled");
    }

    @Test
    void headerAndAdminSidebarPreserveNoticeLinksAlongsideCustomerSupportFeatures() throws IOException {
        String header = file("src/main/resources/templates/fragments/header.html");
        String sidebar = file("src/main/resources/templates/fragments/admin/sidebar.html");

        assertThat(header)
                .contains("th:text=\"#{nav.support}\"")
                .contains("href=\"/support/notices\" th:text=\"#{nav.support.notices}\"")
                .contains("href=\"/support/faq\" th:text=\"#{nav.support.faq}\"")
                .contains("href=\"/support/inquiries\" th:text=\"#{nav.support.inquiry}\"")
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
