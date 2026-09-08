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
 * 마이페이지 공개 화면의 고정 문구가 messages 로 옮겨졌는지 본다.
 *
 * <p>닉네임·이메일·게시글 제목·댓글 내용처럼 사용자가 만든 값과, 여행지·여행정보처럼
 * 이미 DB 번역을 쓰는 값은 messages 대상이 아니다. 관리자 화면(/admin/**)도 대상이 아니다.
 */
class MyPagePublicMessagesContractTest {

    /** 마이페이지 공개 화면이 쓰는 키. 여섯 번들에 모두 있어야 한다. */
    private static final List<String> KEYS = List.of(
            "mypage.title", "mypage.nav.label", "mypage.nav.profile", "mypage.nav.posts",
            "mypage.nav.comments", "mypage.nav.bookmarks", "mypage.nav.inquiries",
            "mypage.nav.account", "mypage.nav.account.social",
            "mypage.filter.all", "mypage.pagination.previous", "mypage.pagination.next",
            "mypage.postType.question", "mypage.postType.tip", "mypage.postType.course",
            "mypage.target.destination", "mypage.target.community", "mypage.profileImageAlt",
            "mypage.index.description", "mypage.index.manageProfile", "mypage.index.menu.title",
            "mypage.index.menu.profile.description", "mypage.index.menu.posts.description",
            "mypage.index.menu.comments.description", "mypage.index.menu.bookmarks.description",
            "mypage.index.menu.inquiries.description", "mypage.index.menu.account.description",
            "mypage.index.menu.account.social.description", "mypage.index.pageTitle",
            "mypage.profile.title", "mypage.profile.description", "mypage.profile.updated",
            "mypage.profile.currentImage", "mypage.profile.currentImage.help",
            "mypage.profile.image", "mypage.profile.image.help",
            "mypage.profile.image.choose", "mypage.profile.image.noneSelected",
            "mypage.profile.nickname",
            "mypage.profile.nickname.help.format", "mypage.profile.nickname.help.forbidden",
            "mypage.profile.cancel", "mypage.profile.save", "mypage.profile.pageTitle",
            "mypage.profile.nickname.status.AVAILABLE", "mypage.profile.nickname.status.CURRENT",
            "mypage.profile.nickname.status.INVALID_FORMAT",
            "mypage.profile.nickname.status.FORBIDDEN",
            "mypage.profile.nickname.status.DUPLICATE",
            "mypage.profile.nickname.client.tooShort", "mypage.profile.nickname.client.tooLong",
            "mypage.profile.nickname.client.invalidChars",
            "mypage.profile.nickname.client.checking",
            "mypage.profile.nickname.client.checkFailed",
            "mypage.profile.error.required", "mypage.profile.error.nickname.invalidFormat",
            "mypage.profile.error.nickname.forbidden", "mypage.profile.error.nickname.duplicate",
            "mypage.posts.title", "mypage.posts.description", "mypage.posts.filters.label",
            "mypage.posts.views", "mypage.posts.empty", "mypage.posts.pagination",
            "mypage.posts.pageTitle",
            "mypage.comments.title", "mypage.comments.description",
            "mypage.comments.filters.label", "mypage.comments.reply",
            "mypage.comments.createdAtUnknown", "mypage.comments.viewOriginal",
            "mypage.comments.empty", "mypage.comments.pagination", "mypage.comments.pageTitle",
            "mypage.bookmarks.title", "mypage.bookmarks.description",
            "mypage.bookmarks.sections.label", "mypage.bookmarks.section.destination",
            "mypage.bookmarks.section.community", "mypage.bookmarks.section.travelInfo",
            "mypage.bookmarks.scope.label", "mypage.bookmarks.community.filters.label",
            "mypage.bookmarks.imageAlt", "mypage.bookmarks.noImage", "mypage.bookmarks.remove",
            "mypage.bookmarks.remove.label", "mypage.bookmarks.removed",
            "mypage.bookmarks.remove.failed", "mypage.bookmarks.views", "mypage.bookmarks.empty",
            "mypage.bookmarks.pagination", "mypage.bookmarks.pageTitle",
            "mypage.account.cancel", "mypage.account.confirm",
            "mypage.account.currentPassword", "mypage.account.withdraw",
            "mypage.account.social.pageTitle", "mypage.account.social.description",
            "mypage.account.social.oauthError", "mypage.account.social.loginAccount",
            "mypage.account.social.loginAccount.help",
            "mypage.account.social.provider.GOOGLE", "mypage.account.social.provider.KAKAO",
            "mypage.account.social.provider.NAVER", "mypage.account.social.provider.other",
            "mypage.account.social.providerEmail", "mypage.account.social.providerEmail.none",
            "mypage.account.social.empty",
            "mypage.account.verify.pageTitle", "mypage.account.verify.description",
            "mypage.account.verify.required",
            "mypage.account.edit.pageTitle", "mypage.account.edit.description",
            "mypage.account.edit.updated", "mypage.account.edit.info.title",
            "mypage.account.edit.info.help", "mypage.account.edit.username",
            "mypage.account.edit.email", "mypage.account.edit.personal.title",
            "mypage.account.edit.personal.help", "mypage.account.edit.fullName",
            "mypage.account.edit.phone", "mypage.account.edit.birth",
            "mypage.account.edit.submit",
            "mypage.account.password.title", "mypage.account.password.help",
            "mypage.account.password.new", "mypage.account.password.new.help",
            "mypage.account.password.confirm",
            "mypage.account.withdrawal.pageTitle", "mypage.account.withdrawal.summary",
            "mypage.account.withdrawal.impact.title",
            "mypage.account.withdrawal.notice.irreversible",
            "mypage.account.withdrawal.notice.contentKept",
            "mypage.account.withdrawal.notice.bookmarksRemoved",
            "mypage.account.withdrawal.notice.emailReusable",
            "mypage.account.withdrawal.notice.nicknameReusable",
            "mypage.account.withdrawal.notice.usernameNotReusable",
            "mypage.account.withdrawal.notice.socialUnlinked",
            "mypage.account.withdrawal.adminBlocked",
            "mypage.account.withdrawal.social.description",
            "mypage.account.withdrawal.social.verifyAccount",
            "mypage.account.withdrawal.social.help",
            "mypage.account.withdrawal.social.confirmWith",
            "mypage.account.withdrawal.social.expired",
            "mypage.account.error.currentPassword.required",
            "mypage.account.error.currentPassword.mismatch",
            "mypage.account.error.password.required",
            "mypage.account.error.password.confirmRequired",
            "mypage.account.error.password.mismatch",
            "mypage.account.error.password.invalid",
            "mypage.account.error.fullName.required",
            "mypage.account.error.fullName.tooLong",
            "mypage.account.error.phone.invalid",
            "mypage.account.error.social.cannotStart",
            "mypage.account.error.social.accountUnknown",
            "mypage.account.error.social.multipleProviders");

    @ParameterizedTest
    @ValueSource(strings = {"", "_ko", "_en", "_ja", "_zh_CN", "_zh_TW"})
    void everyBundleCarriesEveryMypageKey(String suffix) throws IOException {
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

        for (String key : List.of("mypage.title", "mypage.nav.profile", "mypage.nav.bookmarks",
                "mypage.posts.title", "mypage.comments.title", "mypage.bookmarks.title",
                "mypage.posts.empty", "mypage.comments.empty", "mypage.bookmarks.empty",
                "mypage.profile.save", "mypage.bookmarks.remove",
                "mypage.profile.image.choose", "mypage.profile.image.noneSelected",
                "mypage.account.currentPassword", "mypage.account.withdraw",
                "mypage.account.edit.updated", "mypage.account.password.title",
                "mypage.account.verify.description",
                "mypage.account.withdrawal.notice.irreversible",
                "mypage.account.error.currentPassword.mismatch")) {
            assertThat(translated.getProperty(key)).as("%s in messages%s", key, suffix)
                    .isNotEqualTo(korean.getProperty(key));
        }
    }

    /** 공개 template 과 스크립트는 고정 문구를 직접 쓰지 않는다. (사용자 데이터 출력은 그대로) */
    @Test
    void mypageTemplatesAndScriptsDoNotHardcodeTheLocalizedLabels() throws IOException {
        for (String path : List.of(
                "src/main/resources/templates/mypage/index.html",
                "src/main/resources/templates/mypage/profile.html",
                "src/main/resources/templates/mypage/posts.html",
                "src/main/resources/templates/mypage/comments.html",
                "src/main/resources/templates/mypage/bookmarks.html",
                "src/main/resources/templates/mypage/account-edit.html",
                "src/main/resources/templates/mypage/account-social.html",
                "src/main/resources/templates/mypage/account-verify.html",
                "src/main/resources/templates/mypage/social-withdrawal-confirm.html",
                "src/main/resources/templates/fragments/mypage/navigation.html")) {
            assertThat(Files.readString(Path.of(path), StandardCharsets.UTF_8)).as(path)
                    .contains("#{mypage.");
        }
        // 안내 문구는 data-* 로 받는다. 스크립트가 다시 들고 있으면 안 되는 문구를 직접 짚는다.
        assertThat(Files.readString(
                Path.of("src/main/resources/static/js/mypage-profile.js"), StandardCharsets.UTF_8))
                .contains("nicknameInput.dataset")
                .doesNotContain("닉네임은", "확인 중", "현재 사용 중인 닉네임입니다.",
                        "사용할 수 없는 닉네임입니다.", "이미 사용 중인 닉네임입니다.");
        assertThat(Files.readString(
                Path.of("src/main/resources/static/js/mypage-bookmarks.js"), StandardCharsets.UTF_8))
                .contains("dataset")
                .doesNotContain("북마크를 삭제했습니다.", "삭제하지 못했습니다");
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
