package com.example.travlediary.repository;

import com.example.travlediary.service.bookmark.ContentBookmarkService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BookmarkMapperContractTest {

    @Test
    void bookmarkWritesAreIdempotentAndUseUppercaseTargetTypes() throws IOException {
        String bookmarkXml = resource("/mapper/BookmarkMapper.xml");
        String destinationService = resourceText(
                "src/main/java/com/example/travlediary/service/destination/DestinationBookmarkService.java");

        assertThat(bookmarkXml)
                .contains("INSERT IGNORE INTO bookmarks")
                .contains("WHERE user_id = #{userId}")
                .doesNotContain("'destination'");
        assertThat(destinationService).doesNotContain("\"destination\"");
    }

    @Test
    void detailSqlCalculatesBookmarkForCurrentUserAndGuestSafely() throws IOException {
        assertThat(resource("/mapper/PostMapper.xml"))
                .contains("b.target_type = 'POST'")
                .contains("b.user_id = #{currentUserId}")
                .contains("<otherwise>FALSE</otherwise>")
                .contains("p.post_type IN ('QUESTION', 'TIP')")
                .contains("FOR UPDATE");
        assertThat(resource("/mapper/CourseMapper.xml"))
                .contains("b.target_type = 'COURSE'")
                .contains("b.user_id = #{currentUserId}")
                .contains("<otherwise>FALSE</otherwise>")
                .contains("c.deleted = 0")
                .contains("FOR UPDATE");
    }

    @Test
    void contentBookmarkMutationsAreTransactionalAndSchemaReferenceHasUniqueKey()
            throws NoSuchMethodException, IOException {
        for (String method : new String[]{
                "bookmarkPost", "unbookmarkPost", "bookmarkCourse", "unbookmarkCourse"}) {
            Transactional transactional = ContentBookmarkService.class
                    .getMethod(method, Long.class, Long.class)
                    .getAnnotation(Transactional.class);
            assertThat(transactional).isNotNull();
        }

        assertThat(resourceText("docs/db/travel_diary_schema_reference.md"))
                .contains("uq_bookmarks_user_type_target")
                .contains("(`user_id`,`target_type`,`target_id`)");
    }

    @Test
    void contentBookmarkUiUsesSeparateScriptWithoutSendingTargetType() throws IOException {
        String javascript = resourceText("src/main/resources/static/js/content-bookmarks.js");
        String postDetail = resourceText("src/main/resources/templates/post/detail.html");
        String courseDetail = resourceText("src/main/resources/templates/course/detail.html");

        assertThat(javascript)
                .contains("button.disabled = true")
                .contains("bookmarked ? 'DELETE' : 'POST'")
                .contains("/login?redirect=")
                .contains("button.dataset.bookmarkUrl")
                .doesNotContain("targetType");
        assertThat(postDetail)
                .contains("/js/content-bookmarks.js")
                .contains("/bookmarks/posts/{id}")
                .contains("content-bookmark-button");
        assertThat(courseDetail)
                .contains("/js/content-bookmarks.js")
                .contains("/bookmarks/courses/{id}")
                .contains("content-bookmark-button");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String resourceText(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }
}
