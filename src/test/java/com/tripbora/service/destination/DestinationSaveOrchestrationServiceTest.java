package com.tripbora.service.destination;

import com.tripbora.dto.DestinationForm;
import com.tripbora.dto.kto.KtoSelectedPhotoRequest;
import com.tripbora.service.kto.InvalidKtoSelectedPhotosException;
import com.tripbora.service.kto.KtoPhotoImportService;
import com.tripbora.service.kto.PreparedKtoPhoto;
import com.tripbora.model.DestinationImageCommonsSource;
import com.tripbora.service.wikidata.CommonsPhotoImportService;
import com.tripbora.service.wikidata.PreparedCommonsPhoto;
import com.tripbora.service.wikidata.WikidataRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DestinationSaveOrchestrationServiceTest {

    @Mock private KtoPhotoImportService ktoPhotoImportService;
    @Mock private DestinationSavePersistenceService persistenceService;

    private DestinationSaveOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new DestinationSaveOrchestrationService(
                ktoPhotoImportService, persistenceService);
    }

    @Test
    void rejectsCreateWithDirectAndKtoMainBeforePreparingPhotos() {
        DestinationForm form = formWithDirectUpload(true);
        List<KtoSelectedPhotoRequest> selected = List.of(selected(true));

        assertThatThrownBy(() -> service.registerDestination(form, 7L, selected))
                .isInstanceOf(InvalidKtoSelectedPhotosException.class);

        verifyNoInteractions(ktoPhotoImportService, persistenceService);
    }

    @Test
    void wikidataCommonsPhotosArePreparedBeforeTransactionAndCleanedWhenDbSaveFails() {
        CommonsPhotoImportService commons = org.mockito.Mockito.mock(CommonsPhotoImportService.class);
        WikidataRegistrationService wikidata = org.mockito.Mockito.mock(WikidataRegistrationService.class);
        ReflectionTestUtils.setField(service, "commonsPhotoImportService", commons);
        ReflectionTestUtils.setField(service, "wikidataRegistrationService", wikidata);
        DestinationForm form = new DestinationForm();
        form.setWikidataQid("Q243");
        form.setCommonsSelectedPhotosJson("{\"qid\":\"Q243\",\"photos\":[{\"fileName\":\"A.jpg\",\"main\":true}]}");
        List<CommonsPhotoImportService.Selection> selections =
                List.of(new CommonsPhotoImportService.Selection("A.jpg", true));
        List<PreparedCommonsPhoto> prepared = List.of(new PreparedCommonsPhoto(
                "/uploads/destinations/a.jpg", true, new DestinationImageCommonsSource()));
        when(commons.parseSelections(form.getCommonsSelectedPhotosJson(), "Q243")).thenReturn(selections);
        when(wikidata.prepareRegistration(form, selections, false))
                .thenReturn(new WikidataRegistrationService.PreparedRegistration(Map.of(), prepared));
        doThrow(new IllegalStateException("db failed"))
                .when(persistenceService).registerWikidataDestination(form, 7L, Map.of(), prepared);

        assertThatThrownBy(() -> service.registerDestination(form, 7L, List.of()))
                .isInstanceOf(IllegalStateException.class);

        InOrder order = inOrder(persistenceService, wikidata, commons);
        order.verify(persistenceService).rejectRegisteredWikidata("Q243");
        order.verify(wikidata).prepareRegistration(form, selections, false);
        order.verify(persistenceService).registerWikidataDestination(form, 7L, Map.of(), prepared);
        order.verify(commons).cleanup(prepared);
    }

    @Test
    void alreadyRegisteredQidIsRejectedBeforeAnyRevalidationOrDownload() {
        CommonsPhotoImportService commons = org.mockito.Mockito.mock(CommonsPhotoImportService.class);
        WikidataRegistrationService wikidata = org.mockito.Mockito.mock(WikidataRegistrationService.class);
        ReflectionTestUtils.setField(service, "commonsPhotoImportService", commons);
        ReflectionTestUtils.setField(service, "wikidataRegistrationService", wikidata);
        DestinationForm form = new DestinationForm();
        form.setWikidataQid("Q243");
        form.setCommonsSelectedPhotosJson("{\"qid\":\"Q243\",\"photos\":[{\"fileName\":\"A.jpg\",\"main\":true}]}");
        doThrow(new DuplicateWikidataDestinationException("Q243"))
                .when(persistenceService).rejectRegisteredWikidata("Q243");

        assertThatThrownBy(() -> service.registerDestination(form, 7L, List.of()))
                .isInstanceOf(DuplicateWikidataDestinationException.class);

        verifyNoInteractions(wikidata, commons);
        verify(persistenceService, never()).registerWikidataDestination(any(), any(), any(), any());
    }

    @Test
    void wikidataRegistrationWithoutPhotosDoesNotCallCommons() {
        CommonsPhotoImportService commons = org.mockito.Mockito.mock(CommonsPhotoImportService.class);
        WikidataRegistrationService wikidata = org.mockito.Mockito.mock(WikidataRegistrationService.class);
        ReflectionTestUtils.setField(service, "commonsPhotoImportService", commons);
        ReflectionTestUtils.setField(service, "wikidataRegistrationService", wikidata);
        DestinationForm form = new DestinationForm();
        form.setWikidataQid("Q243");
        when(wikidata.prepareRegistration(form, List.of(), false))
                .thenReturn(new WikidataRegistrationService.PreparedRegistration(Map.of(), List.of()));

        service.registerDestination(form, 7L, List.of());

        verify(persistenceService).registerWikidataDestination(form, 7L, Map.of(), List.of());
        verifyNoInteractions(commons);
    }

    @Test
    void createWithoutKtoUsesTheSamePersistencePathWithEmptyPreparedPhotos() {
        DestinationForm form = new DestinationForm();

        service.registerDestination(form, 7L, List.of());

        verify(persistenceService).registerDestination(form, 7L, List.of());
        verifyNoInteractions(ktoPhotoImportService);
    }

    @Test
    void createPreparesPhotosBeforeStartingPersistence() {
        DestinationForm form = new DestinationForm();
        List<KtoSelectedPhotoRequest> selected = List.of(selected(true));
        List<PreparedKtoPhoto> prepared = List.of(prepared(true));
        when(ktoPhotoImportService.preparePhotos(selected)).thenReturn(prepared);

        service.registerDestination(form, 7L, selected);

        InOrder order = inOrder(ktoPhotoImportService, persistenceService);
        order.verify(ktoPhotoImportService).preparePhotos(selected);
        order.verify(persistenceService).registerDestination(form, 7L, prepared);
        verify(ktoPhotoImportService, never()).cleanupPreparedPhotos(prepared);
    }

    @Test
    void persistenceFailureCleansAllPreparedCreatePhotos() {
        DestinationForm form = new DestinationForm();
        List<KtoSelectedPhotoRequest> selected = List.of(selected(false));
        List<PreparedKtoPhoto> prepared = List.of(prepared(false));
        RuntimeException failure = new RuntimeException("db failure");
        when(ktoPhotoImportService.preparePhotos(selected)).thenReturn(prepared);
        doThrow(failure).when(persistenceService)
                .registerDestination(form, 7L, prepared);

        assertThatThrownBy(() -> service.registerDestination(form, 7L, selected))
                .isSameAs(failure);

        verify(ktoPhotoImportService).cleanupPreparedPhotos(prepared);
    }

    @Test
    void updateKeepsDirectUploadsOutOfTheFlowAndPersistsPreparedKtoPhotos() {
        DestinationForm form = formWithDirectUpload(true);
        List<KtoSelectedPhotoRequest> selected = List.of(selected(true));
        List<PreparedKtoPhoto> prepared = List.of(prepared(true));
        when(ktoPhotoImportService.preparePhotos(selected)).thenReturn(prepared);

        service.updateDestination(9L, form, selected);

        verify(persistenceService).updateDestination(9L, form, prepared);
    }

    private DestinationForm formWithDirectUpload(boolean main) {
        DestinationForm form = new DestinationForm();
        form.setMain(main);
        form.setImages(new MockMultipartFile[]{
                new MockMultipartFile("images", "direct.jpg", "image/jpeg", new byte[]{1})
        });
        return form;
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
