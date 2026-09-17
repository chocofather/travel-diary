package com.example.travlediary.service.diary;

import com.example.travlediary.dto.DiaryCoverLibraryAssetFile;
import com.example.travlediary.dto.DiaryCoverLibraryDetailDto;
import com.example.travlediary.dto.DiaryCoverLibraryMineDto;
import com.example.travlediary.dto.DiaryCoverLibraryPageDto;
import com.example.travlediary.dto.DiaryCoverLibrarySort;
import com.example.travlediary.model.DiaryCoverLibraryElement;
import com.example.travlediary.model.DiaryCoverLibraryItem;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAsset;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAssetStatus;
import com.example.travlediary.model.DiaryCoverLibraryPhotoShareMode;
import com.example.travlediary.repository.diary.DiaryCoverLibraryElementMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryItemMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryPhotoAssetMapper;
import com.example.travlediary.service.file.DiaryCoverLibraryPhotoStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryCoverLibraryQueryServiceTest {

    @Mock private DiaryCoverLibraryItemMapper itemMapper;
    @Mock private DiaryCoverLibraryElementMapper elementMapper;
    @Mock private DiaryCoverLibraryPhotoAssetMapper photoAssetMapper;
    @Mock private DiaryCoverLibraryPhotoStorage photoStorage;

    @Test
    void publishedPageLoadsAllElementsInOneBatchAndBuildsControlledPhotoUrls() {
        DiaryCoverLibraryQueryServiceImpl service = service();
        DiaryCoverLibraryItem first = item(11L, 2);
        DiaryCoverLibraryItem second = item(12L, 1);
        DiaryCoverLibraryElement excluded = photo(101L, 11L, 2,
                DiaryCoverLibraryPhotoShareMode.EXCLUDED, null);
        DiaryCoverLibraryElement included = photo(102L, 12L, 1,
                DiaryCoverLibraryPhotoShareMode.INCLUDED, 701L);
        when(itemMapper.countPublished()).thenReturn(2);
        when(itemMapper.findPublished("LATEST", 0, 12)).thenReturn(List.of(first, second));
        when(elementMapper.findAllByLibraryItemIds(List.of(11L, 12L)))
                .thenReturn(List.of(excluded, included));

        DiaryCoverLibraryPageDto page =
                service.getPublishedPage(DiaryCoverLibrarySort.LATEST, 1);

        assertThat(page.items()).containsExactly(first, second);
        assertThat(page.currentPage()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.elementsByItem().get(11L)).containsExactly(excluded);
        assertThat(page.elementsByItem().get(12L)).containsExactly(included);
        assertThat(excluded.getImageUrl()).isNull();
        assertThat(included.getImageUrl())
                .isEqualTo("/diaries/cover-library/assets/701");

        verify(elementMapper).findAllByLibraryItemIds(List.of(11L, 12L));
        verify(elementMapper, never())
                .findAllByLibraryItemIdAndSnapshotVersion(11L, 2);
        verify(elementMapper, never())
                .findAllByLibraryItemIdAndSnapshotVersion(12L, 1);
    }

    @Test
    void popularPageUsesThePopularSortAndClampsTheRequestedPage() {
        DiaryCoverLibraryQueryServiceImpl service = service();
        when(itemMapper.countPublished()).thenReturn(13);
        when(itemMapper.findPublished("POPULAR", 12, 12)).thenReturn(List.of(item(13L, 1)));
        when(elementMapper.findAllByLibraryItemIds(List.of(13L))).thenReturn(List.of());

        DiaryCoverLibraryPageDto page =
                service.getPublishedPage(DiaryCoverLibrarySort.POPULAR, 99);

        assertThat(page.currentPage()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.sort()).isEqualTo(DiaryCoverLibrarySort.POPULAR);
        verify(itemMapper).findPublished("POPULAR", 12, 12);
    }

    @Test
    void mineReturnsOnlyTheOwnersVisibleManagementItemsWithOneElementBatch() {
        DiaryCoverLibraryQueryServiceImpl service = service();
        DiaryCoverLibraryItem published = item(11L, 2);
        DiaryCoverLibraryItem withdrawn = item(12L, 1);
        DiaryCoverLibraryElement included = photo(101L, 11L, 2,
                DiaryCoverLibraryPhotoShareMode.INCLUDED, 701L);
        when(itemMapper.findManageableByCreatorUserId(7L))
                .thenReturn(List.of(published, withdrawn));
        when(elementMapper.findAllByLibraryItemIds(List.of(11L, 12L)))
                .thenReturn(List.of(included));

        DiaryCoverLibraryMineDto mine = service.getMine(7L);

        assertThat(mine.items()).containsExactly(published, withdrawn);
        assertThat(mine.elementsByItem().get(11L)).containsExactly(included);
        assertThat(mine.elementsByItem().get(12L)).isEmpty();
        assertThat(included.getImageUrl())
                .isEqualTo("/diaries/cover-library/assets/701");
        verify(elementMapper).findAllByLibraryItemIds(List.of(11L, 12L));
    }

    @Test
    void detailOnlyReturnsPublishedItemsAndTheCurrentSnapshot() {
        DiaryCoverLibraryQueryServiceImpl service = service();
        DiaryCoverLibraryItem published = item(11L, 3);
        DiaryCoverLibraryElement element = photo(101L, 11L, 3,
                DiaryCoverLibraryPhotoShareMode.INCLUDED, 701L);
        when(itemMapper.findPublishedById(11L)).thenReturn(published);
        when(elementMapper.findAllByLibraryItemIdAndSnapshotVersion(11L, 3))
                .thenReturn(List.of(element));

        DiaryCoverLibraryDetailDto detail = service.getPublishedDetail(11L);

        assertThat(detail.item()).isSameAs(published);
        assertThat(detail.elements()).containsExactly(element);
        assertThat(element.getImageUrl()).isEqualTo("/diaries/cover-library/assets/701");

        when(itemMapper.findPublishedById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getPublishedDetail(99L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void blockedAssetNeverReachesThePrivateStorage() {
        DiaryCoverLibraryQueryServiceImpl service = service();
        DiaryCoverLibraryPhotoAsset blocked = asset(701L,
                DiaryCoverLibraryPhotoAssetStatus.BLOCKED, "photos/private.jpg");
        when(photoAssetMapper.findById(701L)).thenReturn(blocked);

        assertThatThrownBy(() -> service.getActiveAsset(701L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(photoStorage);
    }

    @Test
    void invalidPrivateAssetPathIsHiddenBehindANotFoundResponse() {
        DiaryCoverLibraryQueryServiceImpl service = service();
        DiaryCoverLibraryPhotoAsset active = asset(701L,
                DiaryCoverLibraryPhotoAssetStatus.ACTIVE, "../../private/secret.jpg");
        when(photoAssetMapper.findById(701L)).thenReturn(active);
        when(photoStorage.resolveForRead(active.getStorageKey()))
                .thenThrow(new IllegalArgumentException("/private/secret.jpg"));

        assertThatThrownBy(() -> service.getActiveAsset(701L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).doesNotContain("/private/");
                });
    }

    @Test
    void activeAssetReturnsOnlySafeResponseMetadata() {
        DiaryCoverLibraryQueryServiceImpl service = service();
        DiaryCoverLibraryPhotoAsset active = asset(701L,
                DiaryCoverLibraryPhotoAssetStatus.ACTIVE,
                "photos/123e4567-e89b-12d3-a456-426614174000.jpg");
        active.setContentType("image/jpeg");
        active.setFileSize(321L);
        Path path = Path.of("private-cover-library", active.getStorageKey());
        when(photoAssetMapper.findById(701L)).thenReturn(active);
        when(photoStorage.resolveForRead(active.getStorageKey())).thenReturn(path);

        DiaryCoverLibraryAssetFile file = service.getActiveAsset(701L);

        assertThat(file.path()).isEqualTo(path);
        assertThat(file.contentType()).isEqualTo("image/jpeg");
        assertThat(file.contentLength()).isEqualTo(321L);
    }

    @Test
    void assetContentTypeMustMatchItsValidatedFileExtension() {
        DiaryCoverLibraryQueryServiceImpl service = service();
        DiaryCoverLibraryPhotoAsset active = asset(701L,
                DiaryCoverLibraryPhotoAssetStatus.ACTIVE,
                "photos/123e4567-e89b-12d3-a456-426614174000.jpg");
        active.setContentType("image/png");
        when(photoAssetMapper.findById(701L)).thenReturn(active);

        assertThatThrownBy(() -> service.getActiveAsset(701L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(photoStorage);
    }

    private DiaryCoverLibraryQueryServiceImpl service() {
        return new DiaryCoverLibraryQueryServiceImpl(
                itemMapper, elementMapper, photoAssetMapper, photoStorage);
    }

    private DiaryCoverLibraryItem item(Long id, int snapshotVersion) {
        DiaryCoverLibraryItem item = new DiaryCoverLibraryItem();
        item.setId(id);
        item.setSnapshotVersion(snapshotVersion);
        item.setTitle("공유 표지 " + id);
        return item;
    }

    private DiaryCoverLibraryElement photo(
            Long id, Long itemId, int snapshotVersion,
            DiaryCoverLibraryPhotoShareMode mode, Long assetId) {
        DiaryCoverLibraryElement element = new DiaryCoverLibraryElement();
        element.setId(id);
        element.setLibraryItemId(itemId);
        element.setSnapshotVersion(snapshotVersion);
        element.setElementType("PHOTO");
        element.setPhotoShareMode(mode);
        element.setPhotoAssetId(assetId);
        return element;
    }

    private DiaryCoverLibraryPhotoAsset asset(
            Long id, DiaryCoverLibraryPhotoAssetStatus status, String storageKey) {
        DiaryCoverLibraryPhotoAsset asset = new DiaryCoverLibraryPhotoAsset();
        asset.setId(id);
        asset.setStatus(status);
        asset.setStorageKey(storageKey);
        asset.setContentType("image/jpeg");
        asset.setFileSize(321L);
        return asset;
    }
}
