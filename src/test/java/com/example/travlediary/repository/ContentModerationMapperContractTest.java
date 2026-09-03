package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관리자 조치 SQL 계약.
 * 기존 사용자 삭제 SQL 의 user_id 조건이 유지되는지도 함께 고정한다.
 */
class ContentModerationMapperContractTest {

    @Test
    void adminHideAndRestoreNeverFilterByOwner() throws IOException {
        String mapper = resource("/mapper/ContentModerationMapper.xml");
        String hide = between(mapper, "<update id=\"hideTarget\"", "</update>");
        String restore = between(mapper, "<update id=\"restoreTarget\"", "</update>");

        assertThat(hide)
                .contains("deleted = 1", "deleted_at = NOW()", "WHERE id = #{targetId}")
                .contains("AND deleted = 0")
                .doesNotContain("user_id");
        assertThat(restore)
                .contains("deleted = 0", "deleted_at = NULL", "WHERE id = #{targetId}")
                .contains("AND deleted = 1")
                .doesNotContain("user_id");
    }

    @Test
    void everySupportedTargetTableIsWhitelisted() throws IOException {
        String table = between(resource("/mapper/ContentModerationMapper.xml"),
                "<sql id=\"TargetTable\"", "</sql>");

        assertThat(table)
                .contains("'POST'", "user_posts")
                .contains("'COURSE'", "courses")
                .contains("'POST_COMMENT'", "post_comments")
                .contains("'COURSE_COMMENT'", "course_comments")
                .contains("'DESTINATION_COMMENT'", "destination_comments");
    }

    @Test
    void moderationHistoryKeepsActiveAndRestoredStates() throws IOException {
        String mapper = resource("/mapper/ContentModerationMapper.xml");
        String insert = between(mapper, "<insert id=\"insert\"", "</insert>");
        String restore = between(mapper, "<update id=\"restoreModeration\"", "</update>");

        assertThat(insert).contains("target_type", "target_id", "target_user_id",
                "status", "reason", "admin_note", "created_by");
        assertThat(restore)
                .contains("status = 'RESTORED'", "restored_at = #{restoredAt}",
                        "restored_by = #{restoredBy}", "restore_reason = #{restoreReason}")
                .contains("AND status = 'ACTIVE'");
    }

    @Test
    void userDeletionMappersStillRequireTheOwner() throws IOException {
        assertThat(between(resource("/mapper/PostMapper.xml"),
                "<update id=\"softDeletePost\"", "</update>")).contains("user_id = #{userId}");
        assertThat(between(resource("/mapper/CourseMapper.xml"),
                "<update id=\"softDeleteCourse\"", "</update>")).contains("user_id = #{userId}");
        assertThat(between(resource("/mapper/PostCommentMapper.xml"),
                "<update id=\"softDelete\"", "</update>")).contains("user_id = #{userId}");
        assertThat(between(resource("/mapper/CourseCommentMapper.xml"),
                "<update id=\"softDelete\"", "</update>")).contains("user_id = #{userId}");
    }

    @Test
    void commentListsExposeTheModeratedFlagForPlaceholders() throws IOException {
        assertThat(between(resource("/mapper/PostCommentMapper.xml"),
                "<sql id=\"commentDtoColumns\">", "</sql>"))
                .contains("cm.target_type = 'POST_COMMENT'")
                .contains("cm.status = 'ACTIVE'")
                .contains("AS moderated");
        assertThat(between(resource("/mapper/CourseCommentMapper.xml"),
                "<sql id=\"commentDtoColumns\">", "</sql>"))
                .contains("cm.target_type = 'COURSE_COMMENT'")
                .contains("cm.status = 'ACTIVE'")
                .contains("AS moderated");
    }

    @Test
    void moderatedRepliesStayInTheTreeForPostAndCourseComments() throws IOException {
        // 원댓글뿐 아니라 자식댓글/대댓글도 조치 후 목록에 남아야 한다
        assertThat(between(resource("/mapper/PostCommentMapper.xml"),
                "<select id=\"findRepliesForRootComments\"", "</select>"))
                .contains("pc.deleted = 0")
                .contains("<include refid=\"ModeratedComment\"><property name=\"alias\" value=\"pc\"/></include>");
        assertThat(between(resource("/mapper/CourseCommentMapper.xml"),
                "<select id=\"findRepliesForRootComments\"", "</select>"))
                .contains("cc.deleted = 0")
                .contains("<include refid=\"ModeratedComment\"><property name=\"alias\" value=\"cc\"/></include>");

        // 단일 조회 목록의 자식 분기에도 동일 조건이 적용된다
        assertThat(between(resource("/mapper/PostCommentMapper.xml"),
                "<select id=\"findByPostId\"", "</select>"))
                .contains("pc.parent_comment_id IS NOT NULL")
                .contains("value=\"reply\"");
        assertThat(between(resource("/mapper/CourseCommentMapper.xml"),
                "<select id=\"findByCourseId\"", "</select>"))
                .contains("cc.parent_comment_id IS NOT NULL")
                .contains("value=\"reply\"");
    }

    @Test
    void moderatedRootsSurviveEvenWithoutLiveReplies() throws IOException {
        for (String[] target : new String[][]{
                {"/mapper/PostCommentMapper.xml", "pc"},
                {"/mapper/CourseCommentMapper.xml", "cc"}}) {
            String paged = between(resource(target[0]), "<select id=\"findPagedRootComments\"", "</select>");
            String count = between(resource(target[0]), "<select id=\"countRootCommentThreads\"", "</select>");
            String alias = "<include refid=\"ModeratedComment\"><property name=\"alias\" value=\""
                    + target[1] + "\"/></include>";
            // 목록과 개수 조건이 같아야 페이징이 어긋나지 않는다
            assertThat(paged).as(target[0]).contains(alias).contains("value=\"active_reply\"");
            assertThat(count).as(target[0]).contains(alias).contains("value=\"active_reply\"");
        }
    }

    @Test
    void userDeletedCommentsKeepTheExistingPolicy() throws IOException {
        for (String path : new String[]{"/mapper/PostCommentMapper.xml",
                "/mapper/CourseCommentMapper.xml"}) {
            String mapper = resource(path);
            // 조치 판별은 ACTIVE content_moderations 가 있는 댓글로 한정된다
            assertThat(between(mapper, "<sql id=\"ModeratedComment\">", "</sql>")).as(path)
                    .contains("FROM content_moderations cm")
                    .contains("cm.status = 'ACTIVE'")
                    .contains("cm.target_id = ${alias}.id");
            // 표시용 댓글 수는 기존대로 살아있는 댓글만 센다
            assertThat(between(mapper, "<select id=\"countActiveComments\"", "</select>")).as(path)
                    .contains("AND deleted = 0")
                    .doesNotContain("content_moderations");
        }
    }

    @Test
    void destinationCommentRepliesNeedNoDepthFix() throws IOException {
        // 여행지 댓글 목록은 부모/자식 구분 없이 한 번에 조회하므로
        // 이미 자식 조치 댓글도 포함된다
        for (String selectId : new String[]{"findByDestinationIdWithWriter",
                "findByDestinationIdOrderByCreatedAtDesc", "findByDestinationIdOrderByLikesDesc"}) {
            assertThat(between(resource("/mapper/DestinationCommentMapper.xml"),
                    "<select id=\"" + selectId + "\"", "</select>")).as(selectId)
                    .contains("AND (c.deleted = 0 OR <include refid=\"ModeratedExists\"/>)")
                    .doesNotContain("parent_comment_id IS NULL");
        }
    }

    @Test
    void destinationCommentListsKeepModeratedRowsButHideTheirContent() throws IOException {
        String mapper = resource("/mapper/DestinationCommentMapper.xml");

        // 관리자 조치분만 목록에 남기고 사용자 직접 삭제분은 기존처럼 제외한다
        assertThat(between(mapper, "<sql id=\"ModeratedExists\">", "</sql>"))
                .contains("cm.target_type = 'DESTINATION_COMMENT'")
                .contains("cm.status = 'ACTIVE'");
        assertThat(mapper)
                .contains("AND (c.deleted = 0 OR <include refid=\"ModeratedExists\"/>)")
                .contains("CASE WHEN c.deleted = 1 THEN NULL ELSE c.content END AS content");

        for (String selectId : new String[]{"findByDestinationIdWithWriter",
                "findByDestinationIdOrderByCreatedAtDesc", "findByDestinationIdOrderByLikesDesc"}) {
            assertThat(between(mapper, "<select id=\"" + selectId + "\"", "</select>"))
                    .as(selectId)
                    .contains("AND (c.deleted = 0 OR <include refid=\"ModeratedExists\"/>)")
                    .contains("<include refid=\"ModeratedFlag\"/>");
        }
    }

    @Test
    void destinationCommentUserDeletionAndCountsAreUnchanged() throws IOException {
        String mapper = resource("/mapper/DestinationCommentMapper.xml");

        // 사용자 삭제 경로와 그 외 조회는 기존 deleted = 0 조건을 그대로 유지한다
        assertThat(between(mapper, "<update id=\"updateDeleted\"", "</update>"))
                .contains("deleted = true")
                .doesNotContain("content_moderations");
        assertThat(mapper).contains("AND deleted = 0");
    }

    @Test
    void moderationButtonsAreRenderedOnlyForAdmins() throws IOException {
        String helper = resource("/static/js/admin-content-moderation.js");
        assertThat(helper)
                .contains("isAdmin")
                .contains("/admin/contents/${targetType}/${targetId}/hide")
                .contains("meta[name=\"_csrf\"]")
                .contains("조치 사유는 필수입니다.");

        for (String path : new String[]{"/static/js/post-comments.js",
                "/static/js/course-comments.js", "/static/js/comment/render.js"}) {
            assertThat(resource(path)).as(path)
                    .contains("window.adminModeration?.isAdminUser()")
                    .contains("window.adminModeration.makeButton");
        }
        assertThat(resource("/static/js/post-comments.js")).contains("'POST_COMMENT'");
        assertThat(resource("/static/js/course-comments.js")).contains("'COURSE_COMMENT'");
        assertThat(resource("/static/js/comment/render.js")).contains("'DESTINATION_COMMENT'");
    }

    @Test
    void commentHideReasonIsAskedInAModalInsteadOfAPrompt() throws IOException {
        String helper = resource("/static/js/admin-content-moderation.js");

        // 사유 입력은 prompt 가 아니라 커스텀 모달로 받는다
        assertThat(helper)
                .doesNotContain("window.prompt")
                .contains("overlay.className = 'content-moderation-modal'")
                .contains("'댓글 숨김'")
                .contains("'이 댓글을 숨김 처리하시겠습니까?'")
                .contains("'사유를 입력해 주세요.'")
                // 열 때 대상 댓글을 보관하고 입력창에 focus, 닫을 때 함께 비운다
                .contains("target = {targetType, targetId};")
                .contains("modal.reason.focus();")
                .contains("ui.reason.value = '';")
                .contains("target = null;")
                // backdrop / 닫기 버튼 / 취소 / Escape 로 닫는다 (내부 클릭은 제외)
                .contains("if (event.target === overlay) close();")
                .contains("closeButton.addEventListener('click', close);")
                .contains("cancel.addEventListener('click', close);")
                .contains("event.key === 'Escape'")
                // 공백 사유는 요청하지 않고, 중복 제출을 막는다
                .contains("if (!reason.trim())")
                .contains("ui.submit.disabled = true;");

        // 요청 형식(엔드포인트/필드명/CSRF)은 그대로다
        String submitForm = between(helper, "function submitHideForm(", "function makeButton(");
        assertThat(submitForm)
                .contains("form.method = 'post'")
                .contains("/admin/contents/${targetType}/${targetId}/hide")
                .contains("reason: reason")
                .contains("adminNote")
                .contains("redirect: window.location.pathname + window.location.search")
                .contains("meta[name=\"_csrf\"]");

        // 모달 스타일은 세 댓글 화면이 모두 로드하는 공용 CSS 에 둔다
        assertThat(resource("/static/css/content-comment.css"))
                .contains(".content-moderation-modal[hidden]")
                .contains(".content-moderation-dialog")
                .contains("max-width: 460px")
                .contains(".content-moderation-modal-submit");
        for (String path : new String[]{"/templates/destination/detail.html",
                "/templates/post/detail.html", "/templates/course/detail.html"}) {
            assertThat(resource(path)).as(path).contains("/css/content-comment.css");
        }
    }

    @Test
    void moderatedRepliesAreNotFilteredOutWhenTheTreeIsBuilt() throws IOException {
        // 자식 댓글 그룹핑에서 관리자 조치분을 제외하면 화면에서 사라진다
        for (String path : new String[]{"/static/js/post-comments.js",
                "/static/js/course-comments.js"}) {
            String script = resource(path);
            assertThat(script).as(path)
                    .contains("comment.parentCommentId != null\n"
                            + "                && (!comment.deleted || comment.moderated)");
            // 사용자 직접 삭제 대댓글을 되살리는 형태가 아니어야 한다
            assertThat(script).as(path).doesNotContain("parentCommentId != null)");
        }
    }

    @Test
    void rootAndReplyShareTheSamePlaceholderRenderer() throws IOException {
        for (String path : new String[]{"/static/js/post-comments.js",
                "/static/js/course-comments.js"}) {
            String script = resource(path);
            // 루트는 필터 없이 모두 유지되고, 자식은 renderComment(reply, true) 로 같은 렌더러를 탄다
            assertThat(script).as(path)
                    .contains("filter(comment => comment.parentCommentId == null)")
                    .contains("renderComment(reply, true)");
        }
    }

    @Test
    void destinationCommentTreeGroupingHasNoDeletedFilter() throws IOException {
        assertThat(between(resource("/static/js/comment/render.js"), "groupByParent", "}, {});"))
                .contains("c.parentCommentId ?? null")
                .doesNotContain("deleted");
    }

    @Test
    void moderatedCommentsKeepTheTreeAndShowThePlaceholder() throws IOException {
        assertThat(resource("/static/js/post-comments.js")).contains("관리자에 의해 조치된 댓글입니다.");
        // 여행지·코스 댓글은 조치 문구도 화면이 내려준 현재 언어 문구를 쓴다
        for (String path : new String[]{"/static/js/comment/render.js",
                "/static/js/course-comments.js"}) {
            assertThat(resource(path)).as(path)
                    .contains("detailMessage('commentModerated')")
                    .doesNotContain("관리자에 의해 조치된 댓글입니다.");
        }
        // 사용자 직접 삭제 문구는 그대로 남아 있어야 한다
        assertThat(resource("/static/js/post-comments.js")).contains("삭제된 댓글입니다.");
        assertThat(resource("/static/js/course-comments.js"))
                .contains("detailMessage('commentDeleted')");
    }

    @Test
    void contentTypeFilterAppliesOnChangeWithoutTouchingTheSearchFlow() throws IOException {
        assertThat(resource("/static/js/admin-content-filter.js"))
                .contains("admin-content-filter-form")
                .contains("select[name=\"targetType\"]")
                .contains("addEventListener('change', () => form.submit())")
                .doesNotContain("keyword");
    }

    @Test
    void moderatedContentListShowsOnlyActiveAdminActions() throws IOException {
        String mapper = resource("/mapper/ContentModerationMapper.xml");

        // 사용자 직접 삭제분은 조치 행이 없어 조인 자체가 되지 않는다
        assertThat(between(mapper, "<sql id=\"ModeratedContentFilter\">", "</sql>"))
                .contains("WHERE cm.status = 'ACTIVE'")
                .contains("AND cm.target_type = #{targetType}")
                .contains("author.nickname LIKE CONCAT('%', #{keyword}, '%')")
                .contains("t.title LIKE", "t.content_snippet LIKE");

        // 대상 5종의 식별 정보를 모두 모은다
        String targets = between(mapper, "<sql id=\"ModeratedTargets\">", "</sql>");
        assertThat(targets)
                .contains("'POST' AS target_type", "FROM user_posts p")
                .contains("'COURSE'", "FROM courses c")
                .contains("'POST_COMMENT'", "FROM post_comments pc")
                .contains("'COURSE_COMMENT'", "FROM course_comments cc")
                .contains("'DESTINATION_COMMENT'", "FROM destination_comments dc");

        String list = between(mapper, "<select id=\"findModeratedContents\"", "</select>");
        assertThat(list)
                .contains("<include refid=\"ModeratedContentFilter\"/>")
                .contains("cm.reason", "cm.created_at", "admin_name", "author_name")
                .contains("t.parent_id")
                .contains("ORDER BY cm.created_at DESC");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
