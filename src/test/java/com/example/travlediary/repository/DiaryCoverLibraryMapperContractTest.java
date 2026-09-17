package com.example.travlediary.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DiaryCoverLibraryMapperContractTest {

    @Test
    void itemMapperPersistsTheCompletePublishedSnapshot() throws IOException {
        String insert = between(resource("/mapper/DiaryCoverLibraryItemMapper.xml"),
                "<insert id=\"insert\"", "</insert>");

        assertThat(insert).contains(
                "creator_user_id", "creator_display_name", "source_cover_design_id",
                "base_cover_style", "background_color", "snapshot_version");
    }

    @Test
    void photoAssetAndElementMappersKeepThePrivateAssetLink() throws IOException {
        String assetInsert = between(resource("/mapper/DiaryCoverLibraryPhotoAssetMapper.xml"),
                "<insert id=\"insert\"", "</insert>");
        assertThat(assetInsert).contains(
                "storage_key", "content_type", "file_size",
                "rights_confirmed_at", "rights_terms_version");

        String elementInsert = between(resource("/mapper/DiaryCoverLibraryElementMapper.xml"),
                "<insert id=\"insert\"", "</insert>");
        assertThat(elementInsert).contains(
                "snapshot_version", "photo_share_mode", "photo_asset_id",
                "position_x", "position_y", "width", "height", "rotation", "z_index");
    }

    @Test
    void downloadMapperUsesTheUniqueMemberHistoryTable() throws IOException {
        String mapper = resource("/mapper/DiaryCoverLibraryDownloadMapper.xml");

        assertThat(mapper).contains("INSERT IGNORE INTO diary_cover_library_downloads")
                .contains("library_item_id")
                .contains("downloader_user_id")
                .contains("first_downloaded_at");
    }

    @Test
    void downloadLocksThePublishedItemAndIncrementsOnlyWhilePublished() throws IOException {
        String mapper = resource("/mapper/DiaryCoverLibraryItemMapper.xml");
        String locked = between(
                mapper, "<select id=\"findPublishedByIdForUpdate\"", "</select>");
        String increment = between(
                mapper, "<update id=\"incrementDownloadCountIfPublished\"", "</update>");

        assertThat(locked).contains("status = 'PUBLISHED'").contains("FOR UPDATE");
        assertThat(increment).contains("download_count = download_count + 1")
                .contains("status = 'PUBLISHED'");
    }

    @Test
    void downloadedDesignMappersPersistTheLibrarySourceAndSharedPhotoAsset() throws IOException {
        String designInsert = between(resource("/mapper/DiaryCoverDesignMapper.xml"),
                "<insert id=\"insert\"", "</insert>");
        String elementInsert = between(resource("/mapper/DiaryCoverDesignElementMapper.xml"),
                "<insert id=\"insert\"", "</insert>");
        String appliedElementInsert = between(resource("/mapper/DiaryCoverElementMapper.xml"),
                "<insert id=\"insert\"", "</insert>");

        assertThat(designInsert).contains("source_library_item_id");
        assertThat(elementInsert).contains("library_photo_asset_id");
        assertThat(appliedElementInsert).contains("library_photo_asset_id");
    }

    @Test
    void downloadLocksPhotoAssetStatusesBeforeCopyingTheirReferences() throws IOException {
        String mapper = resource("/mapper/DiaryCoverLibraryPhotoAssetMapper.xml");
        String locked = between(mapper,
                "<select id=\"findAllByLibraryItemIdAndSnapshotVersionForUpdate\"",
                "</select>");

        assertThat(locked).contains("library_item_id = #{libraryItemId}")
                .contains("snapshot_version = #{snapshotVersion}")
                .contains("FOR UPDATE");
    }

    @Test
    void publishedLibraryListUsesTheEstablishedLatestAndPopularIndexes() throws IOException {
        String mapper = resource("/mapper/DiaryCoverLibraryItemMapper.xml");
        String list = between(mapper, "<select id=\"findPublished\"", "</select>");

        assertThat(list).contains("status = 'PUBLISHED'")
                .contains("download_count DESC, published_at DESC, id DESC")
                .contains("published_at DESC, id DESC")
                .contains("LIMIT #{limit} OFFSET #{offset}");

        String count = between(mapper, "<select id=\"countPublished\"", "</select>");
        assertThat(count).contains("status = 'PUBLISHED'");

        String detail = between(mapper, "<select id=\"findPublishedById\"", "</select>");
        assertThat(detail).contains("id = #{itemId}").contains("status = 'PUBLISHED'");
    }

    @Test
    void authorManagementLocksTheItemAndUsesOnlyAllowedSoftStatusTransitions()
            throws IOException {
        String mapper = resource("/mapper/DiaryCoverLibraryItemMapper.xml");
        String mine = between(
                mapper, "<select id=\"findManageableByCreatorUserId\"", "</select>");
        String locked = between(
                mapper, "<select id=\"findByIdForUpdate\"", "</select>");
        String withdraw = between(
                mapper, "<update id=\"withdrawByOwner\"", "</update>");
        String republish = between(
                mapper, "<update id=\"republishByOwner\"", "</update>");
        String delete = between(
                mapper, "<update id=\"softDeleteByOwner\"", "</update>");

        assertThat(mine).contains("creator_user_id = #{creatorUserId}")
                .contains("status &lt;&gt; 'DELETED'");
        assertThat(locked).contains("id = #{itemId}").contains("FOR UPDATE");
        assertThat(withdraw).contains("status = 'WITHDRAWN'")
                .contains("status = 'PUBLISHED'")
                .contains("creator_user_id = #{creatorUserId}");
        assertThat(republish).contains("status = 'PUBLISHED'")
                .contains("status = 'WITHDRAWN'")
                .contains("withdrawn_at = NULL")
                .doesNotContain("published_at");
        assertThat(delete).contains("status = 'DELETED'")
                .contains("deleted_at = #{deletedAt}")
                .contains("status = #{expectedStatus}")
                .doesNotContain("DELETE FROM")
                .doesNotContain("download_count");
    }

    @Test
    void libraryElementsAreReadInOneBatchForTheCurrentSnapshotVersions() throws IOException {
        String mapper = resource("/mapper/DiaryCoverLibraryElementMapper.xml");
        String select = between(
                mapper, "<select id=\"findAllByLibraryItemIds\"", "</select>");

        assertThat(select).contains("JOIN diary_cover_library_items")
                .contains("e.snapshot_version = i.snapshot_version")
                .contains("e.library_item_id IN")
                .contains("ORDER BY e.library_item_id ASC, e.z_index ASC, e.id ASC");
    }

    @Test
    void reportMapperPersistsServerVerifiedSnapshotTargetsAndLocksForProcessing()
            throws IOException {
        String mapper = resource("/mapper/DiaryCoverLibraryReportMapper.xml");
        String insert = between(mapper, "<insert id=\"insert\"", "</insert>");
        String locked = between(mapper,
                "<select id=\"findByIdForUpdate\"", "</select>");
        String processed = between(mapper,
                "<update id=\"markProcessed\"", "</update>");

        assertThat(insert).contains("reported_snapshot_version", "photo_asset_id",
                "reporter_user_id", "reason_code", "description", "status");
        assertThat(locked).contains("WHERE id = #{reportId}").contains("FOR UPDATE");
        assertThat(processed).contains("processed_by_user_id = #{processedByUserId}")
                .contains("processed_at = #{processedAt}")
                .contains("resolution_action = #{resolutionAction}")
                .contains("status = 'PENDING'");
    }

    @Test
    void moderationMappersUseConditionalBlocksAndAppendOnlyActions() throws IOException {
        String itemMapper = resource("/mapper/DiaryCoverLibraryItemMapper.xml");
        String itemBlock = between(itemMapper,
                "<update id=\"blockByAdmin\"", "</update>");
        assertThat(itemBlock).contains("status = 'BLOCKED'", "blocked_at = #{blockedAt}",
                "blocked_reason = #{blockedReason}", "status = #{expectedStatus}");

        String assetMapper = resource("/mapper/DiaryCoverLibraryPhotoAssetMapper.xml");
        String assetLock = between(assetMapper,
                "<select id=\"findByIdForUpdate\"", "</select>");
        String assetBlock = between(assetMapper,
                "<update id=\"blockByAdmin\"", "</update>");
        assertThat(assetLock).contains("WHERE id = #{assetId}").contains("FOR UPDATE");
        assertThat(assetBlock).contains("status = 'BLOCKED'", "blocked_at = #{blockedAt}",
                "blocked_reason = #{blockedReason}", "status = 'ACTIVE'");

        String actionInsert = between(
                resource("/mapper/DiaryCoverLibraryModerationActionMapper.xml"),
                "<insert id=\"insert\"", "</insert>");
        assertThat(actionInsert).contains("report_id", "library_item_id", "photo_asset_id",
                "action_type", "previous_status", "resulting_status", "reason",
                "admin_note", "action_by_user_id");
    }

    @Test
    void restoreMappersClearBlockMetadataAndReuseTheLatestBlockAction()
            throws IOException {
        String itemMapper = resource("/mapper/DiaryCoverLibraryItemMapper.xml");
        String itemRestore = between(itemMapper,
                "<update id=\"restoreByAdmin\"", "</update>");
        assertThat(itemRestore).contains("status = #{restoredStatus}")
                .contains("blocked_at = NULL")
                .contains("blocked_reason = NULL")
                .contains("status = 'BLOCKED'");

        String assetMapper = resource("/mapper/DiaryCoverLibraryPhotoAssetMapper.xml");
        String photoRestore = between(assetMapper,
                "<update id=\"restoreByAdmin\"", "</update>");
        assertThat(photoRestore).contains("status = 'ACTIVE'")
                .contains("blocked_at = NULL")
                .contains("blocked_reason = NULL")
                .contains("status = 'BLOCKED'");

        String actionMapper = resource(
                "/mapper/DiaryCoverLibraryModerationActionMapper.xml");
        String latestItemBlock = between(actionMapper,
                "<select id=\"findLatestBlockItemByLibraryItemId\"", "</select>");
        String latestPhotoBlock = between(actionMapper,
                "<select id=\"findLatestBlockPhotoByPhotoAssetId\"", "</select>");
        assertThat(latestItemBlock).contains("action_type = 'BLOCK_ITEM'")
                .contains("ORDER BY created_at DESC, id DESC")
                .contains("LIMIT 1");
        assertThat(latestPhotoBlock).contains("action_type = 'BLOCK_PHOTO'")
                .contains("ORDER BY created_at DESC, id DESC")
                .contains("LIMIT 1");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertThat(startIndex).as("start %s", start).isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("end %s", end).isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
