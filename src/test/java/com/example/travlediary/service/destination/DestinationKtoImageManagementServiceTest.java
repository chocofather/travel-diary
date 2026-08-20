package com.example.travlediary.service.destination;

import com.example.travlediary.dto.kto.KtoSelectedPhotoRequest;
import com.example.travlediary.service.kto.KtoPhotoDownloadException;
import com.example.travlediary.service.kto.KtoPhotoImportPersistenceService;
import com.example.travlediary.service.kto.KtoPhotoImportService;
import com.example.travlediary.service.kto.PreparedKtoPhoto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DestinationKtoImageManagementServiceTest {

    @Mock private KtoPhotoImportService importService;
    @Mock private KtoPhotoImportPersistenceService persistenceService;

    private DestinationKtoImageManagementService service;

    @BeforeEach
    void setUp() {
        service = new DestinationKtoImageManagementService(importService, persistenceService);
    }

    @Test
    void emptySelectionDoesNotPrepareOrPersist() {
        service.addPhotos(10L, List.of());

        verifyNoInteractions(importService, persistenceService);
    }

    @Test
    void preparesOutsidePersistenceThenStoresForExistingDestination() {
        List<KtoSelectedPhotoRequest> selected = List.of(selected(true));
        List<PreparedKtoPhoto> prepared = List.of(prepared(true));
        when(importService.preparePhotos(selected)).thenReturn(prepared);

        service.addPhotos(10L, selected);

        InOrder order = inOrder(importService, persistenceService);
        order.verify(importService).preparePhotos(selected);
        order.verify(persistenceService).persistPhotos(10L, prepared);
        verify(importService, never()).cleanupPreparedPhotos(prepared);
    }

    @Test
    void prepareFailureNeverStartsPersistence() {
        List<KtoSelectedPhotoRequest> selected = List.of(selected(false));
        KtoPhotoDownloadException failure = new KtoPhotoDownloadException();
        when(importService.preparePhotos(selected)).thenThrow(failure);

        assertThatThrownBy(() -> service.addPhotos(10L, selected)).isSameAs(failure);

        verifyNoInteractions(persistenceService);
    }

    @Test
    void persistenceFailureCleansEveryPreparedFileAndKeepsOriginalFailure() {
        List<KtoSelectedPhotoRequest> selected = List.of(selected(false));
        List<PreparedKtoPhoto> prepared = List.of(prepared(false));
        RuntimeException failure = new RuntimeException("db failure");
        when(importService.preparePhotos(selected)).thenReturn(prepared);
        doThrow(failure).when(persistenceService).persistPhotos(10L, prepared);

        assertThatThrownBy(() -> service.addPhotos(10L, selected)).isSameAs(failure);

        verify(importService).cleanupPreparedPhotos(prepared);
    }

    private KtoSelectedPhotoRequest selected(boolean main) {
        return new KtoSelectedPhotoRequest(
                "content-1",
                "https://tong.visitkorea.or.kr/cms2/website/10/source.jpg",
                "경복궁",
                "촬영자",
                main);
    }

    private PreparedKtoPhoto prepared(boolean main) {
        return new PreparedKtoPhoto(
                "/uploads/destinations/11111111-1111-4111-8111-111111111111.jpg",
                "https://tong.visitkorea.or.kr/cms2/website/10/source.jpg",
                "content-1",
                "경복궁",
                "촬영자",
                main,
                Timestamp.from(Instant.parse("2026-08-21T03:04:05Z")));
    }
}
