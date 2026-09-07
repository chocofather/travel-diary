package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 고객센터 공개 화면의 고정 문구가 messages 로 옮겨졌는지 본다.
 *
 * <p>공지 제목·본문, FAQ 질문·답변·카테고리명처럼 번역 테이블에서 오는 값과
 * 사용자가 쓴 1:1 문의 내용은 messages 대상이 아니다. 관리자 화면(/admin/**)도 한국어 고정이라 대상이 아니다.
 */
class SupportPublicMessagesContractTest {

    /** 고객센터 공개 화면이 쓰는 키. 여섯 번들에 모두 있어야 한다. */
    private static final List<String> KEYS = List.of(
            "support.title", "support.nav.label",
            "support.notice.title", "support.notice.description", "support.notice.pin",
            "support.notice.column.title", "support.notice.column.createdAt",
            "support.notice.column.views", "support.notice.views", "support.notice.empty",
            "support.notice.pagination", "support.notice.pagination.previous",
            "support.notice.pagination.next", "support.notice.pageTitle",
            "support.notice.detail.breadcrumb", "support.notice.detail.createdAt",
            "support.notice.detail.views", "support.notice.detail.content",
            "support.notice.detail.backToList", "support.notice.detail.pageTitle",
            "support.faq.title", "support.faq.description", "support.faq.empty",
            "support.faq.pageTitle",
            "support.inquiry.title", "support.inquiry.description", "support.inquiry.tabs.label",
            "support.inquiry.tab.new", "support.inquiry.tab.list",
            "support.inquiry.column.status", "support.inquiry.column.type",
            "support.inquiry.column.subject", "support.inquiry.column.createdAt",
            "support.inquiry.empty", "support.inquiry.empty.action",
            "support.inquiry.pagination", "support.inquiry.pagination.previous",
            "support.inquiry.pagination.next", "support.inquiry.pageTitle.list",
            "support.inquiry.pageTitle.create", "support.inquiry.pageTitle.edit",
            "support.inquiry.detail.pageTitle",
            "support.inquiry.status.PENDING", "support.inquiry.status.IN_PROGRESS",
            "support.inquiry.status.ANSWERED", "support.inquiry.status.CANCELLED",
            "support.inquiry.type.ACCOUNT", "support.inquiry.type.TRAVEL_INFO",
            "support.inquiry.type.COMMUNITY", "support.inquiry.type.ERROR",
            "support.inquiry.type.OTHER",
            "support.inquiry.detail.breadcrumb", "support.inquiry.detail.createdAt",
            "support.inquiry.detail.myInquiry", "support.inquiry.detail.answer",
            "support.inquiry.detail.answeredAt", "support.inquiry.detail.waiting",
            "support.inquiry.detail.edit", "support.inquiry.detail.delete",
            "support.inquiry.detail.deleteConfirm", "support.inquiry.detail.backToList",
            "support.inquiry.form.title.create", "support.inquiry.form.title.edit",
            "support.inquiry.form.description.create", "support.inquiry.form.description.edit",
            "support.inquiry.form.type", "support.inquiry.form.type.placeholder",
            "support.inquiry.form.subject", "support.inquiry.form.subject.placeholder",
            "support.inquiry.form.content", "support.inquiry.form.content.placeholder",
            "support.inquiry.form.content.help", "support.inquiry.form.cancel",
            "support.inquiry.form.submit.create", "support.inquiry.form.submit.edit",
            "support.inquiry.error.form.required", "support.inquiry.error.type.required",
            "support.inquiry.error.subject.required", "support.inquiry.error.subject.tooLong",
            "support.inquiry.error.content.required", "support.inquiry.error.content.tooLong",
            "support.inquiry.error.edit.answered", "support.inquiry.error.edit.notPending");

    @ParameterizedTest
    @ValueSource(strings = {"", "_ko", "_en", "_ja", "_zh_CN", "_zh_TW"})
    void everyBundleCarriesEverySupportKey(String suffix) throws IOException {
        Properties bundle = bundle("/messages" + suffix + ".properties");

        for (String key : KEYS) {
            assertThat(bundle.getProperty(key)).as("%s in messages%s", key, suffix)
                    .isNotNull().isNotBlank();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"_en", "_ja", "_zh_CN", "_zh_TW"})
    void translatedBundlesDoNotJustRepeatTheKoreanLabels(String suffix) throws IOException {
        Properties korean = bundle("/messages_ko.properties");
        Properties translated = bundle("/messages" + suffix + ".properties");

        // 화면에서 바로 눈에 띄는 문구는 실제로 번역돼 있어야 한다.
        for (String key : List.of("support.title", "support.notice.title", "support.faq.title",
                "support.notice.empty", "support.faq.empty", "support.inquiry.empty",
                "support.inquiry.tab.new", "support.inquiry.tab.list",
                "support.inquiry.status.PENDING", "support.inquiry.status.ANSWERED",
                "support.inquiry.detail.waiting", "support.inquiry.detail.deleteConfirm")) {
            assertThat(translated.getProperty(key)).as("%s in messages%s", key, suffix)
                    .isNotEqualTo(korean.getProperty(key));
        }
    }

    /** 공개 template 은 고정 문구를 직접 쓰지 않는다. (DB 콘텐츠 출력은 그대로 둔다) */
    @Test
    void supportTemplatesDoNotHardcodeTheLocalizedLabels() throws IOException {
        for (String path : List.of(
                "src/main/resources/templates/support/faq.html",
                "src/main/resources/templates/support/notices/list.html",
                "src/main/resources/templates/support/notices/detail.html",
                "src/main/resources/templates/support/inquiries/list.html",
                "src/main/resources/templates/support/inquiries/form.html",
                "src/main/resources/templates/support/inquiries/detail.html",
                "src/main/resources/templates/fragments/support/navigation.html",
                "src/main/resources/templates/fragments/support/inquiry-tabs.html")) {
            String template = Files.readString(Path.of(path), StandardCharsets.UTF_8);

            assertThat(template).as(path)
                    .contains("#{support.")
                    // 상태·유형은 저장값(enum 이름)으로 문구만 고른다
                    .doesNotContain("status.displayName", "inquiryType.displayName",
                            "type.displayName")
                    // 이벤트 핸들러에 언어별 문자열을 박지 않는다
                    .doesNotContain("confirm('");
        }
    }

    private Properties bundle(String path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
