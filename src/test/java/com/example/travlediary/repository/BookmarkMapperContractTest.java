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
                "bookmarkPost", "unbookmarkPost", "bookmarkCourse", "unbookmarkCourse",
                "bookmarkTravelInfo", "unbookmarkTravelInfo"}) {
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
    void travelInfoUsesExistingGenericTableWithBoundedBatchLookupAndTargetCleanup()
            throws IOException {
        String bookmarkXml = resource("/mapper/BookmarkMapper.xml");
        String targetTypes = resourceText(
                "src/main/java/com/example/travlediary/model/BookmarkTargetType.java");

        assertThat(targetTypes).contains("TRAVEL_INFO");
        assertThat(bookmarkXml)
                .contains("<select id=\"findBookmarkedTargetIds\"")
                .contains("user_id = #{userId}", "target_type = #{targetType}")
                .contains("<when test=\"targetIds != null and !targetIds.isEmpty()\">")
                .contains("AND target_id IN")
                .contains("<foreach collection=\"targetIds\"")
                .contains("#{targetId}")
                .contains("<otherwise>", "AND 1 = 0")
                .contains("<delete id=\"deleteByTarget\"");
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

    @Test
    void csrfProtectionCoversAllBookmarkAndAdminNoticeMutations() throws IOException {
        String security = resourceText(
                "src/main/java/com/example/travlediary/config/SecurityConfig.java");
        String layout = resource("/templates/layout/main.html");
        String javascript = resource("/static/js/travel-info-bookmark.js");

        assertThat(security)
                .contains("requireCsrfProtectionMatcher")
                .contains("^/bookmarks$")
                .contains("^/bookmarks/destinations/[0-9]+$")
                .contains("^/bookmarks/posts/[0-9]+$")
                .contains("^/bookmarks/courses/[0-9]+$")
                .contains("^/bookmarks/travel-info/[0-9]+$")
                .contains("^/admin/notices$")
                .contains("^/admin/notices/[0-9]+/edit$")
                .contains("^/admin/notices/[0-9]+/delete$")
                .contains("^/logout$")
                .contains("HttpMethod.POST.name()", "HttpMethod.DELETE.name()")
                .doesNotContain("csrf(AbstractHttpConfigurer::disable)");
        assertThat(layout)
                .contains("name=\"_csrf\"", "name=\"_csrf_header\"")
                .contains("${_csrf.token}", "${_csrf.headerName}");
        assertThat(javascript)
                .contains("[csrfHeader]: csrfToken")
                .contains("credentials: 'same-origin'");
        assertThat(resource("/static/js/bookmark.js"))
                .contains("[csrfHeader]: csrfToken")
                .contains("credentials: 'same-origin'");
        assertThat(resource("/static/js/content-bookmarks.js"))
                .contains("[csrfHeader]: csrfToken")
                .contains("credentials: 'same-origin'");
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
