package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MyPageCommentMapperContractTest {

    private static final Path MAPPER =
            Path.of("src/main/resources/mapper/MyPageCommentMapper.xml");

    @Test
    void unifiesAllCommentTablesWithMatchingProjectionAndCorrectCourseTimestamp() throws IOException {
        String xml = compact(read());

        assertThat(xml)
                .contains("FROM destination_comments dc")
                .contains("FROM post_comments pc")
                .contains("FROM course_comments cc")
                .contains("cc.create_at AS createdAt")
                .doesNotContain("cc.created_at")
                .doesNotContain("reply_to_comment_id");
        assertThat(occurrences(xml, "UNION ALL")).isEqualTo(2);
        assertThat(occurrences(xml, "AS commentId")).isEqualTo(3);
        assertThat(occurrences(xml, "AS commentType")).isEqualTo(3);
        assertThat(occurrences(xml, "AS contentPreview")).isEqualTo(3);
        assertThat(occurrences(xml, "AS createdAt")).isEqualTo(3);
        assertThat(occurrences(xml, "AS reply")).isEqualTo(3);
        assertThat(occurrences(xml, "AS targetId")).isEqualTo(3);
        assertThat(occurrences(xml, "AS targetTitle")).isEqualTo(3);
        assertThat(occurrences(xml, "AS postType")).isEqualTo(3);
    }

    @Test
    void appliesOwnershipDeletionAndActiveOriginalConditionsWithoutFilteringDeletedParents() throws IOException {
        String xml = compact(read());

        assertThat(xml)
                .contains("dc.user_id = #{userId}")
                .contains("pc.user_id = #{userId}")
                .contains("cc.user_id = #{userId}")
                .contains("dc.deleted = 0")
                .contains("pc.deleted = 0")
                .contains("cc.deleted = 0")
                .contains("p.deleted = 0")
                .contains("p.deleted_at IS NULL")
                .contains("c.deleted = 0")
                .contains("c.deleted_at IS NULL")
                .doesNotContain("parent.deleted");
    }

    @Test
    void joinsOriginalTitlesAndSharesExactlyTheSameUnionForListAndCount() throws IOException {
        String xml = compact(read());

        assertThat(xml)
                .contains("JOIN destinations d ON d.id = dc.destination_id")
                .contains("JOIN destination_translations dt")
                .contains("dt.language_code = 'ko'")
                .contains("JOIN user_posts p ON p.id = pc.post_id")
                .contains("JOIN courses c ON c.id = cc.course_id")
                .contains("id=\"findMyComments\"")
                .contains("id=\"countMyComments\"")
                .contains("my_comments.commentType = #{commentType}");
        assertThat(occurrences(xml, "<include refid=\"unifiedMyCommentSelect\"/>")).isEqualTo(2);
        assertThat(occurrences(xml, "<include refid=\"commentTypeFilter\"/>")).isEqualTo(2);
    }

    @Test
    void usesPreviewStableOrderingAndFixedPageParameters() throws IOException {
        String xml = compact(read());

        assertThat(xml)
                .contains("LEFT(dc.content, 200)")
                .contains("LEFT(pc.content, 200)")
                .contains("LEFT(cc.content, 200)")
                .contains("ORDER BY my_comments.createdAt DESC, my_comments.commentType ASC, my_comments.commentId DESC")
                .contains("LIMIT #{limit} OFFSET #{offset}");
    }

    private String read() throws IOException {
        return Files.readString(MAPPER, StandardCharsets.UTF_8);
    }

    private String compact(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private int occurrences(String value, String fragment) {
        return (value.length() - value.replace(fragment, "").length()) / fragment.length();
    }
}
