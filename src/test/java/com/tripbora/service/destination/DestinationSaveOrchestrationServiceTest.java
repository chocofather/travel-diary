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
    @Mock private DestinationDuplicateService duplicateService;

    private DestinationSaveOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new DestinationSaveOrchestrationService(
                ktoPhotoImportService, persistenceService);
        ReflectionTestUtils.setField(service, "duplicateService", duplicateService);
        // 따로 정하지 않으면 저장 직전 공통 판별은 미등록이다.
        org.mockito.Mockito.lenient().when(duplicateService.check(any()))
                .thenReturn(DestinationDuplicateCheck.NOT_REGISTERED);
    }

    /** 등록폼에서 Wikidata 단건 후보를 골라 저장할 때도 목록과 같은 공통 판별을 외부 재검증 전에 한다. */
    @Test
    void aWikidataSingleRegistrationIsCheckedBeforeExternalRevalidation() {
        WikidataRegistrationService wikidata = org.mockito.Mockito.mock(WikidataRegistrationService.class);
        ReflectionTestUtils.setField(service, "wikidataRegistrationService", wikidata);
        DestinationForm form = new DestinationForm();
        form.setWikidataQid(" q484637 ");
        form.getTranslations().get(0).setName("경복궁");
        form.setRegionId(11L);
        when(duplicateService.check(any())).thenReturn(possible(5L));

        assertThatThrownBy(() -> service.registerDestination(form, 7L, List.of()))
                .isInstanceOf(DuplicateDestinationException.class)
                .satisfies(exception -> org.assertj.core.api.Assertions.assertThat(
                        ((DuplicateDestinationException) exception).getCheck().destinationId()).isEqualTo(5L));

        org.mockito.ArgumentCaptor<DestinationDuplicateQuery> query =
                org.mockito.ArgumentCaptor.forClass(DestinationDuplicateQuery.class);
        verify(duplicateService).check(query.capture());
        org.assertj.core.api.Assertions.assertThat(query.getValue().sourceType())
                .isEqualTo(DestinationService.WIKIDATA_SOURCE_TYPE);
        org.assertj.core.api.Assertions.assertThat(query.getValue().externalContentId()).isEqualTo("Q484637");
        org.assertj.core.api.Assertions.assertThat(query.getValue().names()).contains("경복궁");
        org.assertj.core.api.Assertions.assertThat(query.getValue().regionId()).isEqualTo(11L);
        verifyNoInteractions(wikidata);
        verify(persistenceService, never()).registerWikidataDestination(any(), any(), any(), any());
    }

    /** '다른 여행지 확인'을 체크한 중복 확인 후보는 저장하고, 확정 중복은 체크해도 막는다. */
    @Test
    void anAcknowledgedPossibleDuplicateIsSavedButAConfirmedDuplicateIsNever() {
        DestinationForm acknowledged = new DestinationForm();
        acknowledged.setKtoContentId("126508");
        acknowledged.setAllowPossibleDuplicate(true);
        DestinationForm confirmed = new DestinationForm();
        confirmed.setKtoContentId("126509");
        confirmed.setAllowPossibleDuplicate(true);
        when(duplicateService.check(org.mockito.ArgumentMatchers.argThat(query -> query != null
                && "126508".equals(query.externalContentId())))).thenReturn(possible(5L));
        when(duplicateService.check(org.mockito.ArgumentMatchers.argThat(query -> query != null
                && "126509".equals(query.externalContentId())))).thenReturn(new DestinationDuplicateCheck(
                DestinationDuplicateStatus.REGISTERED, DestinationDuplicateReason.EXTERNAL_CONTENT_ID,
                6L, "창덕궁", null, "같은 외부 콘텐츠 ID"));

        service.registerDestination(acknowledged, 7L, List.of());
        assertThatThrownBy(() -> service.registerDestination(confirmed, 7L, List.of()))
                .isInstanceOf(DuplicateDestinationException.class);

        verify(persistenceService).registerDestination(acknowledged, 7L, "126508", List.of());
        verify(persistenceService, never()).registerDestination(confirmed, 7L, "126509", List.of());
    }

    /** 외부 후보 없이 직접 입력한 등록은 이름이 겹쳐도 막지 않는다(기존 동작). */
    @Test
    void aFullyManualRegistrationIsNotCheckedByName() {
        DestinationForm form = new DestinationForm();
        form.getTranslations().get(0).setName("경복궁");

        service.registerDestination(form, 7L, List.of());

        verify(duplicateService, never()).check(any());
        verify(persistenceService).registerDestination(form, 7L, List.of());
    }

    /**
     * JSON 일괄등록은 외부 ID가 없는 관리자 입력형 여행지도 저장 직전에 같은 공통 판별을 거친다.
     * 확인하지 않은 중복 가능성은 막고, 확인했으면 저장하고 새 여행지 번호를 돌려준다.
     */
    @Test
    void anImportedDestinationWithoutExternalIdIsStillCheckedRightBeforeSaving() {
        DestinationForm unconfirmed = new DestinationForm();
        unconfirmed.getTranslations().get(0).setName("경복궁");
        unconfirmed.setRegionId(11L);
        DestinationForm acknowledged = new DestinationForm();
        acknowledged.getTranslations().get(0).setName("경복궁");
        acknowledged.setRegionId(11L);
        acknowledged.setAllowPossibleDuplicate(true);
        when(duplicateService.check(any())).thenReturn(possible(5L));
        when(persistenceService.registerDestination(acknowledged, 7L, null, List.of())).thenReturn(42L);

        assertThatThrownBy(() -> service.registerImportedDestination(unconfirmed, 7L))
                .isInstanceOf(DuplicateDestinationException.class);
        Long saved = service.registerImportedDestination(acknowledged, 7L);

        org.assertj.core.api.Assertions.assertThat(saved).isEqualTo(42L);
        org.mockito.ArgumentCaptor<DestinationDuplicateQuery> query =
                org.mockito.ArgumentCaptor.forClass(DestinationDuplicateQuery.class);
        verify(duplicateService, org.mockito.Mockito.times(2)).check(query.capture());
        // 외부 ID 없이 이름·지역으로 판별한다.
        org.assertj.core.api.Assertions.assertThat(query.getValue().sourceType()).isNull();
        org.assertj.core.api.Assertions.assertThat(query.getValue().externalContentId()).isNull();
        org.assertj.core.api.Assertions.assertThat(query.getValue().names()).contains("경복궁");
        org.assertj.core.api.Assertions.assertThat(query.getValue().regionId()).isEqualTo(11L);
        verify(persistenceService, never()).registerDestination(unconfirmed, 7L, null, List.of());
        // 기존 관리자 직접 등록 경로(3인자)는 쓰지 않는다.
        verify(persistenceService, never()).registerDestination(any(), any(), org.mockito.ArgumentMatchers.<List<PreparedKtoPhoto>>any());
    }

    /** JSON 일괄등록이라도 확정 중복(같은 contentId)은 확인 여부와 상관없이 저장하지 않는다. */
    @Test
    void anImportedConfirmedDuplicateIsNeverSaved() {
        DestinationForm form = new DestinationForm();
        form.setKtoContentId("126508");
        form.setAllowPossibleDuplicate(true);
        when(duplicateService.check(any())).thenReturn(new DestinationDuplicateCheck(
                DestinationDuplicateStatus.REGISTERED, DestinationDuplicateReason.EXTERNAL_CONTENT_ID,
                6L, "경복궁", null, "같은 외부 콘텐츠 ID"));

        assertThatThrownBy(() -> service.registerImportedDestination(form, 7L))
                .isInstanceOf(DuplicateDestinationException.class);
        verify(persistenceService, never()).registerDestination(any(), any(), any(), any());
    }

    private DestinationDuplicateCheck possible(Long destinationId) {
        return new DestinationDuplicateCheck(DestinationDuplicateStatus.POSSIBLE_DUPLICATE,
                DestinationDuplicateReason.NAME_AND_NEARBY, destinationId, "경복궁", 70, "같은 이름 · 가까운 위치 (약 70m)");
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

    /** 등록폼에서 TourAPI 후보를 고른 경우 contentId 를 저장 경로까지 넘긴다(KTO_TOURAPI + contentId). */
    @Test
    void aSelectedTourApiContentIdIsKeptUntilTheSave() {
        DestinationForm form = new DestinationForm();
        form.setKtoContentId(" 126508 ");

        service.registerDestination(form, 7L, List.of());

        verify(persistenceService).registerDestination(form, 7L, "126508", List.of());
        verify(persistenceService, never()).registerDestination(form, 7L, List.of());
    }

    @Test
    void anInvalidOrConflictingTourApiContentIdIsRejectedBeforeAnySave() {
        DestinationForm invalid = new DestinationForm();
        invalid.setKtoContentId("126508<script>");
        DestinationForm withWikidata = new DestinationForm();
        withWikidata.setKtoContentId("126508");
        withWikidata.setWikidataQid("Q243");

        assertThatThrownBy(() -> service.registerDestination(invalid, 7L, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contentId");
        assertThatThrownBy(() -> service.registerDestination(withWikidata, 7L, List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(persistenceService, ktoPhotoImportService);
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
