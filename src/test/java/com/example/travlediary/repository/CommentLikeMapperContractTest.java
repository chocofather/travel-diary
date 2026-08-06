package com.example.travlediary.repository;

import com.example.travlediary.dto.CourseCommentDto;
import com.example.travlediary.dto.PostCommentDto;
import com.example.travlediary.service.course.CourseCommentServiceImpl;
import com.example.travlediary.service.post.PostCommentServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CommentLikeMapperContractTest {

    @Test
    void commentDtosDefaultToZeroLikesAndNotLikedByMe() {
        PostCommentDto postComment = new PostCommentDto();
        CourseCommentDto courseComment = new CourseCommentDto();

        assertThat(postComment.getLikeCount()).isZero();
        assertThat(postComment.isLikedByMe()).isFalse();
        assertThat(courseComment.getLikeCount()).isZero();
        assertThat(courseComment.isLikedByMe()).isFalse();
    }

    @Test
    void postCommentSqlUsesLikeRowsWithoutDuplicatingCommentRows() throws IOException {
        String xml = resource("/mapper/PostCommentMapper.xml");

        assertThat(xml)
                .contains("FROM post_comment_likes pcl")
                .contains("WHERE pcl.comment_id = pc.id")
                .contains("FROM post_comment_likes my_like")
                .contains("AND my_like.user_id = #{currentUserId}")
                .contains("<when test=\"currentUserId != null\">")
                .contains("WHEN pc.deleted = 1 THEN 0")
                .contains("AS likeCount")
                .contains("AS likedByMe")
                .contains("INSERT IGNORE INTO post_comment_likes")
                .contains("DELETE FROM post_comment_likes")
                .contains("AS replyToNickname")
                .contains("AS replyToDeleted")
                .contains("reply.parent_comment_id = pc.id")
                .contains("parent.parent_comment_id IS NULL")
                .contains("ORDER BY")
                .doesNotContain("JOIN post_comment_likes")
                .doesNotContain("SET likes");
        assertThat(occurrences(xml, "<include refid=\"commentDtoColumns\"/>")).isEqualTo(2);
    }

    @Test
    void courseCommentSqlUsesLikeRowsWithoutDuplicatingCommentRows() throws IOException {
        String xml = resource("/mapper/CourseCommentMapper.xml");

        assertThat(xml)
                .contains("FROM course_comment_likes ccl")
                .contains("WHERE ccl.comment_id = cc.id")
                .contains("FROM course_comment_likes my_like")
                .contains("AND my_like.user_id = #{currentUserId}")
                .contains("<when test=\"currentUserId != null\">")
                .contains("WHEN cc.deleted = 1 THEN 0")
                .contains("AS likeCount")
                .contains("AS likedByMe")
                .contains("INSERT IGNORE INTO course_comment_likes")
                .contains("DELETE FROM course_comment_likes")
                .contains("AS replyToNickname")
                .contains("AS replyToDeleted")
                .contains("reply.parent_comment_id = cc.id")
                .contains("parent.parent_comment_id IS NULL")
                .contains("ORDER BY")
                .doesNotContain("JOIN course_comment_likes")
                .doesNotContain("SET likes");
        assertThat(occurrences(xml, "<include refid=\"commentDtoColumns\"/>")).isEqualTo(2);
    }

    @Test
    void likeAndUnlikeServiceMethodsAreTransactional() throws NoSuchMethodException {
        assertTransactional(PostCommentServiceImpl.class, "likeComment");
        assertTransactional(PostCommentServiceImpl.class, "unlikeComment");
        assertTransactional(CourseCommentServiceImpl.class, "likeComment");
        assertTransactional(CourseCommentServiceImpl.class, "unlikeComment");
    }

    private void assertTransactional(Class<?> serviceType, String methodName) throws NoSuchMethodException {
        Transactional transactional = serviceType
                .getMethod(methodName, Long.class, Long.class)
                .getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private int occurrences(String value, String target) {
        return (value.length() - value.replace(target, "").length()) / target.length();
    }
}
