package com.example.travlediary.service.destination;

import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.service.kto.KtoPhotoImportPersistenceService;
import com.example.travlediary.service.kto.PreparedKtoPhoto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DestinationSavePersistenceServiceTest {

    @Mock private DestinationService destinationService;
    @Mock private KtoPhotoImportPersistenceService ktoPersistenceService;

    private DestinationSavePersistenceService service;

    @BeforeEach
    void setUp() {
        service = new DestinationSavePersistenceService(
                destinationService, ktoPersistenceService);
    }

    @Test
    void registerUsesGeneratedDestinationIdForPreparedKtoPhotos() {
        DestinationForm form = new DestinationForm();
        List<PreparedKtoPhoto> prepared = List.of(prepared());
        when(destinationService.registerDestination(form, 7L)).thenReturn(42L);

        service.registerDestination(form, 7L, prepared);

        InOrder order = inOrder(destinationService, ktoPersistenceService);
        order.verify(destinationService).registerDestination(form, 7L);
        order.verify(ktoPersistenceService).persistPhotos(42L, prepared);
    }

    @Test
    void registerWithoutKtoStillUsesTheSameTransactionalMethod() {
        DestinationForm form = new DestinationForm();
        when(destinationService.registerDestination(form, 7L)).thenReturn(42L);

        service.registerDestination(form, 7L, List.of());

        verify(ktoPersistenceService).persistPhotos(42L, List.of());
    }

    @Test
    void updatePersistsKtoPhotosForTheServerPathDestinationId() {
        DestinationForm form = new DestinationForm();
        List<PreparedKtoPhoto> prepared = List.of(prepared());

        service.updateDestination(9L, form, prepared);

        InOrder order = inOrder(destinationService, ktoPersistenceService);
        order.verify(destinationService).updateDestination(9L, form);
        order.verify(ktoPersistenceService).persistPhotos(9L, prepared);
    }

    @Test
    void createAndUpdatePersistenceBoundariesArePublicTransactionalMethods() throws Exception {
        Method register = DestinationSavePersistenceService.class.getMethod(
                "registerDestination", DestinationForm.class, Long.class, List.class);
        Method update = DestinationSavePersistenceService.class.getMethod(
                "updateDestination", Long.class, DestinationForm.class, List.class);

        assertThat(register.getAnnotation(Transactional.class)).isNotNull();
        assertThat(update.getAnnotation(Transactional.class)).isNotNull();
    }

    private PreparedKtoPhoto prepared() {
        return new PreparedKtoPhoto(
                "/uploads/destinations/11111111-1111-4111-8111-111111111111.jpg",
                "https://tong.visitkorea.or.kr/cms2/website/10/source.jpg",
                "content-1",
                "경복궁",
                "촬영자",
                false,
                Timestamp.from(Instant.parse("2026-08-21T03:04:05Z")));
    }
}
