package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CommentPagingContractTest {

    private static final Path PROJECT = Path.of(".");

    @Test
    void postAndCourseMappersPageRootsThenBatchLoadActiveReplies() throws IOException {
        String post = compact(read("src/main/resources/mapper/PostCommentMapper.xml"));
        String course = compact(read("src/main/resources/mapper/CourseCommentMapper.xml"));

        assertPagedMapper(post, "pc.post_id = #{postId}", "pc.created_at", "post_comments");
        assertPagedMapper(course, "cc.course_id = #{courseId}", "cc.create_at", "course_comments");

        assertThat(post).doesNotContain("${sort}");
        assertThat(course)
                .doesNotContain("${sort}")
                .contains("ORDER BY cc.create_at ASC, cc.id ASC")
                .contains("ORDER BY likeCount DESC, cc.create_at DESC, cc.id DESC");
    }

    @Test
    void deletedRootThreadConditionIsSharedBySelectAndCount() throws IOException {
        for (String mapper : new String[]{"PostCommentMapper.xml", "CourseCommentMapper.xml"}) {
            String xml = compact(read("src/main/resources/mapper/" + mapper));
            assertThat(occurrences(xml, "parent_comment_id IS NULL")).isGreaterThanOrEqualTo(2);
            assertThat(occurrences(xml, "OR EXISTS ( SELECT 1")).isGreaterThanOrEqualTo(2);
            assertThat(occurrences(xml, "active_reply.deleted = 0")).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void destinationPagingCountsRootsAndUsesStableSortsAndReplyOrder() throws IOException {
        String xml = compact(read("src/main/resources/mapper/DestinationCommentMapper.xml"));
        String service = read("src/main/java/com/example/travlediary/service/comment/DestinationCommentService.java");

        assertThat(xml)
                .contains("id=\"countRootComments\"")
                .contains("parent_comment_id IS NULL")
                .contains("ORDER BY c.created_at DESC, c.id DESC")
                .contains("ORDER BY c.created_at ASC, c.id ASC")
                .contains("ORDER BY c.likes DESC, c.created_at DESC, c.id DESC")
                .contains("AND c.destination_id = #{destinationId}");
        assertThat(service)
                .contains("countRootComments(destinationId)")
                .contains("countByDestinationId(destinationId)")
                .contains("offset >= totalThreads")
                .contains("new PageResult<>(List.of(), totalThreads")
                .contains("case \"recent\", \"latest\" -> \"latest\"");
    }

    @Test
    void clientsUseServerLastThreadCountAndRaceProtection() throws IOException {
        for (String scriptPath : new String[]{
                "src/main/resources/static/js/post-comments.js",
                "src/main/resources/static/js/course-comments.js"
        }) {
            String script = read(scriptPath);
            assertThat(script)
                    .contains("const pageSize = 5")
                    .contains("let currentSort = 'latest'")
                    .contains("let nextPage = 0")
                    .contains("let isLoading = false")
                    .contains("let isLastPage = false")
                    .contains("requestGeneration")
                    .contains("Boolean(data.last)")
                    .contains("data.totalCommentCount")
                    .contains("sort=${encodeURIComponent(currentSort)}")
                    .contains("replies.sort(compareByCreatedAtAndId)")
                    .doesNotContain("content.length <")
                    .doesNotContain(".filter(comment => comment.parentCommentId == null)\n            .sort(");
        }

        String destination = read("src/main/resources/static/js/comment/init.js");
        assertThat(destination)
                .contains("const pageSize = 5")
                .contains("let currentSort = 'latest'")
                .contains("requestGeneration")
                .contains("Boolean(data.last)")
                .contains("data.totalCommentCount")
                .contains("concatComments(data.content)")
                .doesNotContain("content.length <");
    }

    @Test
    void allDetailPagesExposeTheSameAccessibleToolbar() throws IOException {
        for (String template : new String[]{
                "src/main/resources/templates/destination/detail.html",
                "src/main/resources/templates/post/detail.html",
                "src/main/resources/templates/course/detail.html"
        }) {
            String html = read(template);
            assertThat(html)
                    .contains("content-comment-toolbar")
                    .contains("data-comment-sort=\"latest\"")
                    .contains("data-comment-sort=\"oldest\"")
                    .contains("data-comment-sort=\"likes\"")
                    .contains("aria-pressed=\"true\"")
                    .contains("content-comment-more");
        }
    }

    private void assertPagedMapper(String xml, String contentCondition, String createdColumn,
                                   String tableName) {
        assertThat(xml)
                .contains("id=\"findPagedRootComments\"")
                .contains("id=\"findRepliesForRootComments\"")
                .contains("id=\"countRootCommentThreads\"")
                .contains("id=\"countActiveComments\"")
                .contains(contentCondition)
                .contains("<choose>")
                .contains("test=\"sort == 'oldest'\"")
                .contains("test=\"sort == 'likes'\"")
                .contains("ORDER BY " + createdColumn + " ASC")
                .contains("ORDER BY likeCount DESC, " + createdColumn + " DESC")
                .contains("<foreach item=\"rootId\" collection=\"rootIds\"")
                .contains("active_reply.deleted = 0")
                .contains("FROM " + tableName);
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private int occurrences(String value, String fragment) {
        return (value.length() - value.replace(fragment, "").length()) / fragment.length();
    }
}
