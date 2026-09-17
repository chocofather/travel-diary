package com.example.travlediary.service.diary;

import com.example.travlediary.model.DiaryCoverDesign;
import com.example.travlediary.model.DiaryCoverDesignElement;
import com.example.travlediary.model.DiaryCoverLibraryElement;
import com.example.travlediary.model.DiaryCoverLibraryItem;
import com.example.travlediary.model.DiaryCoverLibraryItemStatus;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAsset;
import com.example.travlediary.model.DiaryCoverLibraryPhotoAssetStatus;
import com.example.travlediary.model.DiaryCoverLibraryPhotoShareMode;
import com.example.travlediary.repository.diary.DiaryCoverDesignElementMapper;
import com.example.travlediary.repository.diary.DiaryCoverDesignMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryDownloadMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryElementMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryItemMapper;
import com.example.travlediary.repository.diary.DiaryCoverLibraryPhotoAssetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiaryCoverLibraryDownloadServiceTest {

    private static final Instant DOWNLOADED_AT = Instant.parse("2026-09-17T04:00:00Z");

    @Mock private CoverLibraryAccessService accessService;
    @Mock private DiaryCoverLibraryItemMapper itemMapper;
    @Mock private DiaryCoverLibraryElementMapper libraryElementMapper;
    @Mock private DiaryCoverLibraryPhotoAssetMapper photoAssetMapper;
    @Mock private DiaryCoverDesignMapper designMapper;
    @Mock private DiaryCoverDesignElementMapper designElementMapper;
    @Mock private DiaryCoverLibraryDownloadMapper downloadMapper;

    private DiaryCoverLibraryDownloadServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DiaryCoverLibraryDownloadServiceImpl(
                accessService, itemMapper, libraryElementMapper, photoAssetMapper,
                designMapper, designElementMapper, downloadMapper,
                Clock.fixed(DOWNLOADED_AT, ZoneOffset.UTC));
    }

    @Test
    void copiesCurrentSnapshotIntoAnIndependentOwnedDesign() {
        preparePublishedDownload(7L);
        DiaryCoverLibraryElement sticker = element(101L, "STICKER");
        sticker.setImageUrl("/images/diary/stickers/pin.svg");
        DiaryCoverLibraryElement note = element(102L, "NOTE");
        note.setTextContent("출발");
        note.setStyleType("DATE_LABEL");
        note.setColorType("SAGE");
        DiaryCoverLibraryElement text = element(103L, "TEXT");
        text.setTextContent("JEJU");
        text.setTextFont("nanum-square");
        text.setTextColor("#123456");
        DiaryCoverLibraryElement excluded = photo(
                104L, DiaryCoverLibraryPhotoShareMode.EXCLUDED, null);
        DiaryCoverLibraryElement included = photo(
                105L, DiaryCoverLibraryPhotoShareMode.INCLUDED, 701L);
        DiaryCoverLibraryElement blocked = photo(
                106L, DiaryCoverLibraryPhotoShareMode.INCLUDED, 702L);
        when(libraryElementMapper.findAllByLibraryItemIdAndSnapshotVersion(11L, 3))
                .thenReturn(List.of(sticker, note, text, excluded, included, blocked));
        when(photoAssetMapper.findAllByLibraryItemIdAndSnapshotVersionForUpdate(11L, 3))
                .thenReturn(List.of(asset(701L, DiaryCoverLibraryPhotoAssetStatus.ACTIVE),
                        asset(702L, DiaryCoverLibraryPhotoAssetStatus.BLOCKED)));

        DiaryCoverDesign created = service.download(7L, 11L);

        assertThat(created.getId()).isEqualTo(901L);
        ArgumentCaptor<DiaryCoverDesign> design = ArgumentCaptor.forClass(DiaryCoverDesign.class);
        verify(designMapper).insert(design.capture());
        assertThat(design.getValue()).satisfies(saved -> {
            assertThat(saved.getUserId()).isEqualTo(7L);
            assertThat(saved.getSourceLibraryItemId()).isEqualTo(11L);
            assertThat(saved.getName()).isEqualTo("여름의 표지");
            assertThat(saved.getBaseCoverStyle()).isEqualTo("LEATHER_BLACK");
            assertThat(saved.getBackgroundColor()).isEqualTo("#123456");
        });

        ArgumentCaptor<DiaryCoverDesignElement> elements =
                ArgumentCaptor.forClass(DiaryCoverDesignElement.class);
        verify(designElementMapper, times(6)).insert(elements.capture());
        assertThat(elements.getAllValues()).extracting(DiaryCoverDesignElement::getElementType)
                .containsExactly("STICKER", "NOTE", "TEXT", "PHOTO", "PHOTO", "PHOTO");
        assertThat(elements.getAllValues().get(0).getImageUrl())
                .isEqualTo("/images/diary/stickers/pin.svg");
        assertThat(elements.getAllValues().get(1)).satisfies(saved -> {
            assertThat(saved.getTextContent()).isEqualTo("출발");
            assertThat(saved.getStyleType()).isEqualTo("DATE_LABEL");
            assertThat(saved.getColorType()).isEqualTo("SAGE");
        });
        assertThat(elements.getAllValues().get(2)).satisfies(saved -> {
            assertThat(saved.getTextContent()).isEqualTo("JEJU");
            assertThat(saved.getTextFont()).isEqualTo("nanum-square");
            assertThat(saved.getTextColor()).isEqualTo("#123456");
        });
        assertThat(elements.getAllValues().get(3)).satisfies(saved -> {
            assertThat(saved.getImageUrl()).isNull();
            assertThat(saved.getLibraryPhotoAssetId()).isNull();
            assertThat(saved.getPhotoStyle()).isEqualTo("POLAROID");
            assertThat(saved.getPositionX()).isEqualByComparingTo("0.12500");
            assertThat(saved.getRotation()).isEqualByComparingTo("12.50");
        });
        assertThat(elements.getAllValues().get(4)).satisfies(saved -> {
            assertThat(saved.getImageUrl()).isNull();
            assertThat(saved.getLibraryPhotoAssetId()).isEqualTo(701L);
        });
        assertThat(elements.getAllValues().get(5)).satisfies(saved -> {
            assertThat(saved.getImageUrl()).isNull();
            assertThat(saved.getLibraryPhotoAssetId()).isNull();
        });
        verify(libraryElementMapper)
                .findAllByLibraryItemIdAndSnapshotVersion(11L, 3);
        verify(downloadMapper).insertIgnore(any());
        verify(itemMapper).incrementDownloadCountIfPublished(11L);
    }

    @Test
    void onlyPublishedItemsCanBeDownloaded() {
        when(accessService.canDownload(7L)).thenReturn(true);
        when(itemMapper.findPublishedByIdForUpdate(11L)).thenReturn(null);

        assertThatThrownBy(() -> service.download(7L, 11L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(libraryElementMapper, photoAssetMapper, designMapper,
                designElementMapper, downloadMapper);
        verify(itemMapper, never()).incrementDownloadCountIfPublished(any());
    }

    @Test
    void downloadPermissionIsDecidedAtTheDedicatedAccessBoundary() {
        when(accessService.canDownload(7L)).thenReturn(false);

        assertThatThrownBy(() -> service.download(7L, 11L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verifyNoInteractions(itemMapper, libraryElementMapper, photoAssetMapper,
                designMapper, designElementMapper, downloadMapper);
    }

    @Test
    void repeatedMemberDownloadsCreateDesignsEveryTimeButCountOnlyNewMembers() {
        when(accessService.canDownload(7L)).thenReturn(true);
        when(accessService.canDownload(8L)).thenReturn(true);
        when(itemMapper.findPublishedByIdForUpdate(11L)).thenReturn(publishedItem());
        when(libraryElementMapper.findAllByLibraryItemIdAndSnapshotVersion(11L, 3))
                .thenReturn(List.of());
        when(photoAssetMapper.findAllByLibraryItemIdAndSnapshotVersionForUpdate(11L, 3))
                .thenReturn(List.of());
        AtomicLong id = new AtomicLong(900L);
        when(designMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, DiaryCoverDesign.class).setId(id.incrementAndGet());
            return 1;
        });
        when(downloadMapper.insertIgnore(any())).thenReturn(1, 0, 1);
        when(itemMapper.incrementDownloadCountIfPublished(11L)).thenReturn(1);

        DiaryCoverDesign first = service.download(7L, 11L);
        DiaryCoverDesign repeated = service.download(7L, 11L);
        DiaryCoverDesign otherMember = service.download(8L, 11L);

        assertThat(List.of(first.getId(), repeated.getId(), otherMember.getId()))
                .containsExactly(901L, 902L, 903L);
        verify(designMapper, times(3)).insert(any());
        verify(downloadMapper, times(3)).insertIgnore(any());
        verify(itemMapper, times(2)).incrementDownloadCountIfPublished(11L);
    }

    @Test
    void elementFailureRollsBackBeforeHistoryAndCountAreWritten() {
        when(accessService.canDownload(7L)).thenReturn(true);
        when(itemMapper.findPublishedByIdForUpdate(11L)).thenReturn(publishedItem());
        when(designMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, DiaryCoverDesign.class).setId(901L);
            return 1;
        });
        when(libraryElementMapper.findAllByLibraryItemIdAndSnapshotVersion(11L, 3))
                .thenReturn(List.of(element(101L, "STICKER")));
        when(photoAssetMapper.findAllByLibraryItemIdAndSnapshotVersionForUpdate(11L, 3))
                .thenReturn(List.of());
        when(designElementMapper.insert(any()))
                .thenThrow(new IllegalStateException("element insert failure"));
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        DiaryCoverLibraryDownloadService transactional = transactionalProxy(transactionManager);

        assertThatThrownBy(() -> transactional.download(7L, 11L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("element insert failure");

        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
        verifyNoInteractions(downloadMapper);
        verify(itemMapper, never()).incrementDownloadCountIfPublished(any());
    }

    private void preparePublishedDownload(Long userId) {
        when(accessService.canDownload(userId)).thenReturn(true);
        when(itemMapper.findPublishedByIdForUpdate(11L)).thenReturn(publishedItem());
        when(designMapper.insert(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, DiaryCoverDesign.class).setId(901L);
            return 1;
        });
        when(designElementMapper.insert(any())).thenReturn(1);
        when(downloadMapper.insertIgnore(any())).thenReturn(1);
        when(itemMapper.incrementDownloadCountIfPublished(11L)).thenReturn(1);
    }

    private DiaryCoverLibraryItem publishedItem() {
        DiaryCoverLibraryItem item = new DiaryCoverLibraryItem();
        item.setId(11L);
        item.setTitle("여름의 표지");
        item.setBaseCoverStyle("LEATHER_BLACK");
        item.setBackgroundColor("#123456");
        item.setStatus(DiaryCoverLibraryItemStatus.PUBLISHED);
        item.setSnapshotVersion(3);
        return item;
    }

    private DiaryCoverLibraryElement photo(
            Long id, DiaryCoverLibraryPhotoShareMode mode, Long assetId) {
        DiaryCoverLibraryElement element = element(id, "PHOTO");
        element.setPhotoStyle("POLAROID");
        element.setPhotoShareMode(mode);
        element.setPhotoAssetId(assetId);
        return element;
    }

    private DiaryCoverLibraryElement element(Long id, String type) {
        DiaryCoverLibraryElement element = new DiaryCoverLibraryElement();
        element.setId(id);
        element.setLibraryItemId(11L);
        element.setSnapshotVersion(3);
        element.setElementType(type);
        element.setPositionX(new BigDecimal("0.12500"));
        element.setPositionY(new BigDecimal("0.25000"));
        element.setWidth(new BigDecimal("0.45000"));
        element.setHeight(new BigDecimal("0.55000"));
        element.setRotation(new BigDecimal("12.50"));
        element.setZIndex(4);
        return element;
    }

    private DiaryCoverLibraryPhotoAsset asset(
            Long id, DiaryCoverLibraryPhotoAssetStatus status) {
        DiaryCoverLibraryPhotoAsset asset = new DiaryCoverLibraryPhotoAsset();
        asset.setId(id);
        asset.setLibraryItemId(11L);
        asset.setSnapshotVersion(3);
        asset.setStatus(status);
        return asset;
    }

    private DiaryCoverLibraryDownloadService transactionalProxy(
            RecordingTransactionManager transactionManager) {
        TransactionInterceptor interceptor = new TransactionInterceptor(
                transactionManager, new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(service);
        proxyFactory.addAdvice(interceptor);
        return (DiaryCoverLibraryDownloadService) proxyFactory.getProxy();
    }

    private static final class RecordingTransactionManager
            extends AbstractPlatformTransactionManager {
        private boolean committed;
        private boolean rolledBack;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed = true;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack = true;
        }
    }
}
