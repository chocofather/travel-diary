package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 커뮤니티 게시글 댓글 사진 저장소 계약.
 * 아직 기존 댓글 저장/조회 흐름에는 연결하지 않는다.
 */
class PostCommentImageMapperContractTest {

    @Test
    void insertStoresCommentIdUrlAndDisplayOrder() throws IOException {
        String insert = between(mapperXml(), "<insert id=\"insert\"", "</insert>");

        assertThat(insert)
                .contains("INSERT INTO post_comment_images")
                .contains("comment_id", "image_url", "display_order")
                .contains("#{commentId}", "#{imageUrl}", "#{displayOrder}")
                .contains("useGeneratedKeys=\"true\"");
    }

    @Test
    void findByCommentIdsReadsManyCommentsAtOnceInDisplayOrder() throws IOException {
        String select = between(mapperXml(), "<select id=\"findByCommentIds\"", "</select>");

        assertThat(select)
                .contains("FROM post_comment_images")
                .contains("comment_id IN")
                .contains("<foreach item=\"commentId\" collection=\"commentIds\"")
                // 댓글별 사진 순서를 보장한다
                .contains("ORDER BY comment_id, display_order")
                // 빈 목록으로 호출해도 IN () 로 깨지지 않는다
                .contains("1 = 0");
    }

    @Test
    void mapperInterfaceMatchesTheStatementsInXml() throws IOException {
        String mapper = readFile("src/main/java/com/example/travlediary/repository/post/"
                + "PostCommentImageMapper.java");

        assertThat(mapper)
                .contains("int insert(PostCommentImage image)")
                .contains("List<PostCommentImage> findByCommentIds("
                        + "@Param(\"commentIds\") List<Long> commentIds)");
        // XML 네임스페이스가 인터페이스와 1:1 로 짝지어져 있어야 한다
        assertThat(mapperXml())
                .contains("namespace=\"com.example.travlediary.repository.post."
                        + "PostCommentImageMapper\"");
    }

    @Test
    void commentAndImageStoragesStaySeparate() throws IOException {
        // 사진은 post_comment_images 매퍼만 다룬다 (댓글 매퍼는 계속 댓글 테이블만)
        assertThat(resource("/mapper/PostCommentMapper.xml"))
                .doesNotContain("post_comment_images");
        assertThat(readFile("src/main/java/com/example/travlediary/model/PostComment.java"))
                .doesNotContain("imageUrl");
    }

    @Test
    void schemaDocumentKeepsCommentImagesInTheirOwnTable() throws IOException {
        String schema = readFile("docs/db/travel_diary_schema_reference.md");

        assertThat(schema)
                .contains("CREATE TABLE `post_comment_images`")
                .contains("UNIQUE KEY `uq_post_comment_images_order` (`comment_id`,`display_order`)")
                .contains("REFERENCES `post_comments` (`id`) ON DELETE CASCADE")
                .contains("`display_order` >= 1")
                .contains("`display_order` <= 3");
        // post_comments 에는 사진 컬럼을 추가하지 않는다
        assertThat(between(schema, "CREATE TABLE `post_comments`", "ENGINE=InnoDB"))
                .doesNotContain("image_url");
    }

    private String mapperXml() throws IOException {
        return resource("/mapper/PostCommentImageMapper.xml");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String readFile(String path) throws IOException {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path)),
                StandardCharsets.UTF_8);
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
