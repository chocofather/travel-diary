package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MyPageAccountMapperContractTest {

    @Test
    void accountProjectionDoesNotExposeSensitiveColumns() throws IOException {
        String select = statement(userXml(), "select", "findAccountDetailsById");

        assertThat(select)
                .contains("username", "user_email AS userEmail", "full_name AS fullName",
                        "user_phone AS userPhone", "user_birth AS userBirth")
                .contains("id = #{id}", "status = 'ACTIVE'", "deleted_at IS NULL")
                .doesNotContain("user_password", "user_role", "verification_token",
                        "reset_token");
    }

    @Test
    void detailsUpdateUsesAnAllowlistAndPrincipalIdCondition() throws IOException {
        String update = statement(userXml(), "update", "updateAccountDetails");

        assertThat(update)
                .contains("full_name = #{fullName}", "user_phone = #{userPhone}",
                        "user_birth = #{userBirth}", "WHERE id = #{id}",
                        "status = 'ACTIVE'", "deleted_at IS NULL")
                .doesNotContain("username =", "user_email =", "nickname =",
                        "profile_image =", "user_password =", "user_role =",
                        "verification_token =", "reset_token =");
    }

    @Test
    void withdrawalReleasesEmailAndNicknameButNeverUsername() throws IOException {
        String update = statement(userXml(), "update", "deactivateAccount");

        assertThat(update)
                .contains("user_email = #{userEmail}", "nickname = #{nickname}",
                        "status = #{status}", "deleted_at = NOW()",
                        "verification_token = NULL", "reset_token = NULL",
                        "reset_token_exp = NULL", "WHERE id = #{id}",
                        "status = 'ACTIVE'", "deleted_at IS NULL")
                .doesNotContain("username =", "full_name =", "user_phone =",
                        "user_birth =", "DELETE FROM users");
    }

    @Test
    void withdrawalLocksTheActiveAccountBeforeMutatingRelatedRows() throws IOException {
        assertThat(statement(userXml(), "select", "findActiveAccountSecurityByIdForUpdate"))
                .contains("WHERE id = #{id}", "status = 'ACTIVE'",
                        "deleted_at IS NULL", "FOR UPDATE");
    }

    @Test
    void withdrawalCleanupTargetsOnlyTheCurrentUsersPrivateActivity() throws IOException {
        assertThat(statement(resource("mapper/BookmarkMapper.xml"), "delete", "deleteAllByUserId"))
                .contains("DELETE FROM bookmarks", "user_id = #{userId}");
        assertThat(statement(resource("mapper/CommentLikeMapper.xml"), "update",
                "decrementDestinationLikeCountsByUserId"))
                .contains("JOIN comment_likes", "cl.user_id = #{userId}",
                        "GREATEST(dc.likes - 1, 0)");
        assertThat(statement(resource("mapper/CommentLikeMapper.xml"), "delete", "deleteAllByUserId"))
                .contains("DELETE FROM comment_likes", "user_id = #{userId}");
        assertThat(statement(resource("mapper/PostCommentMapper.xml"), "delete", "deleteAllLikesByUserId"))
                .contains("DELETE FROM post_comment_likes", "user_id = #{userId}");
        assertThat(statement(resource("mapper/CourseCommentMapper.xml"), "delete", "deleteAllLikesByUserId"))
                .contains("DELETE FROM course_comment_likes", "user_id = #{userId}");
    }

    @Test
    void deactivatedAccountsCannotRequestOrUsePasswordResetTokens() throws IOException {
        assertThat(statement(userXml(), "select", "findByUsernameAndEmail"))
                .contains("status = 'ACTIVE'", "deleted_at IS NULL");
        assertThat(statement(userXml(), "select", "findByResetToken"))
                .contains("status = 'ACTIVE'", "deleted_at IS NULL");
    }

    private String userXml() throws IOException {
        return resource("mapper/UserMapper.xml");
    }

    private String resource(String relativePath) throws IOException {
        return Files.readString(Path.of("src/main/resources").resolve(relativePath),
                StandardCharsets.UTF_8);
    }

    private String statement(String xml, String tag, String id) {
        String open = "<" + tag + " id=\"" + id + "\"";
        int start = xml.indexOf(open);
        int end = xml.indexOf("</" + tag + ">", start);
        assertThat(start).as("%s statement %s", tag, id).isNotNegative();
        assertThat(end).as("%s statement %s closing tag", tag, id).isGreaterThan(start);
        return xml.substring(start, end);
    }
}
