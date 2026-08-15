package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 여행지 댓글 사진 저장소 계약.
 * STEP B 단계라 기존 댓글 저장/조회 흐름에는 아직 연결하지 않는다.
 */
class DestinationCommentImageMapperContractTest {

    @Test
    void insertStoresCommentIdUrlAndDisplayOrder() throws IOException {
        String insert = between(mapperXml(), "<insert id=\"insert\"", "</insert>");

        assertThat(insert)
                .contains("INSERT INTO destination_comment_images")
                .contains("comment_id", "image_url", "display_order")
                .contains("#{commentId}", "#{imageUrl}", "#{displayOrder}")
                .contains("useGeneratedKeys=\"true\"");
    }

    @Test
    void findByCommentIdsReadsManyCommentsAtOnceInDisplayOrder() throws IOException {
        String select = between(mapperXml(), "<select id=\"findByCommentIds\"", "</select>");

        assertThat(select)
                .contains("comment_id IN")
                .contains("<foreach item=\"commentId\" collection=\"commentIds\"")
                // 댓글별 사진 순서를 보장한다
                .contains("ORDER BY comment_id, display_order")
                // 빈 목록으로 호출해도 SQL 이 깨지지 않는다
                .contains("1 = 0");
    }

    @Test
    void galleryQueryExcludesDeletedAndModeratedComments() throws IOException {
        String select = between(mapperXml(), "<select id=\"findGalleryByDestinationId\"", "</select>");

        assertThat(select)
                .contains("FROM destination_comment_images i")
                .contains("JOIN destination_comments c ON c.id = i.comment_id")
                .contains("c.destination_id = #{destinationId}")
                // 삭제·관리자 조치 댓글(deleted = 1)의 사진은 제외
                .contains("AND c.deleted = 0")
                .contains("ORDER BY c.created_at DESC")
                .contains("LIMIT #{limit}");
    }

    @Test
    void newStorageIsNotWiredIntoExistingCommentFlowYet() throws IOException {
        // STEP B 범위: 기존 댓글 매퍼/서비스는 그대로 image_url 을 쓴다
        assertThat(resource("/mapper/DestinationCommentMapper.xml"))
                .doesNotContain("destination_comment_images");
    }

    @Test
    void schemaDocumentKeepsBothTheNewTableAndTheLegacyColumn() throws IOException {
        String schema = readFile("docs/db/travel_diary_schema_reference.md");

        assertThat(schema)
                .contains("CREATE TABLE `destination_comment_images`")
                .contains("UNIQUE KEY `uq_destination_comment_images_order` (`comment_id`,`display_order`)")
                .contains("REFERENCES `destination_comments` (`id`) ON DELETE CASCADE")
                .contains("`display_order` >= 1")
                .contains("`display_order` <= 3");
        // 기존 컬럼은 아직 DB 에 남아 있으므로 문서에서도 유지한다
        assertThat(between(schema, "CREATE TABLE `destination_comments`", "ENGINE=InnoDB"))
                .contains("`image_url` varchar(255) DEFAULT NULL");
    }

    private String mapperXml() throws IOException {
        return resource("/mapper/DestinationCommentImageMapper.xml");
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
