package com.tripbora.service.destination;

import com.tripbora.dto.DestinationForm;
import com.tripbora.model.DestinationImage;
import com.tripbora.model.DestinationImageCommonsSource;
import com.tripbora.model.DestinationTranslation;
import com.tripbora.model.DestinationTranslationSource;
import com.tripbora.repository.destination.DestinationMapper;
import com.tripbora.repository.destination.DestinationTranslationSourceMapper;
import com.tripbora.service.kto.KtoPhotoImportPersistenceService;
import com.tripbora.service.kto.PreparedKtoPhoto;
import com.tripbora.service.pixabay.PixabayPhotoException;
import com.tripbora.service.pixabay.PreparedPixabayPhoto;
import com.tripbora.service.wikidata.CommonsPhotoSelectionException;
import com.tripbora.service.wikidata.PreparedCommonsPhoto;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DestinationSavePersistenceServiceTest {

    @Mock private DestinationService destinationService;
    @Mock private KtoPhotoImportPersistenceService ktoPersistenceService;
    @Mock private DestinationMapper destinationMapper;
    @Mock private DestinationTranslationSourceMapper sourceMapper;
    @Mock private DestinationImageService imageService;

    private DestinationSavePersistenceService service;

    @BeforeEach
    void setUp() {
        service = new DestinationSavePersistenceService(
                destinationService, ktoPersistenceService);
        ReflectionTestUtils.setField(service, "destinationMapper", destinationMapper);
        ReflectionTestUtils.setField(service, "translationSourceMapper", sourceMapper);
        ReflectionTestUtils.setField(service, "destinationImageService", imageService);
    }

    @Test
    void wikidataDuplicateIsCheckedBeforeAnyInsert() {
        DestinationForm form = new DestinationForm();
        form.setWikidataQid("Q243");
        when(destinationMapper.countByExternalContentId("WIKIDATA", "Q243")).thenReturn(1);

        assertThatThrownBy(() -> service.registerWikidataDestination(form, 7L, java.util.Map.of()))
                .isInstanceOf(DuplicateWikidataDestinationException.class);
        verify(destinationService, never()).registerDestination(any(), any(), any(), any());
    }

    @Test
    void preCheckRejectsRegisteredQidButLeavesTheFinalCheckInsideTheTransaction() {
        when(destinationMapper.countByExternalContentId("WIKIDATA", "Q243")).thenReturn(1);

        assertThatThrownBy(() -> service.rejectRegisteredWikidata(" q243 "))
                .isInstanceOf(DuplicateWikidataDestinationException.class);
        service.rejectRegisteredWikidata("not-a-qid");
        verify(destinationMapper, never()).countByExternalContentId("WIKIDATA", "NOT-A-QID");

        // 사전 확인을 통과한 뒤 다른 요청이 먼저 저장했어도 트랜잭션 안에서 다시 막는다.
        DestinationForm form = new DestinationForm();
        form.setWikidataQid("Q243");
        assertThatThrownBy(() -> service.registerWikidataDestination(form, 7L, java.util.Map.of(), List.of()))
                .isInstanceOf(DuplicateWikidataDestinationException.class);
        verify(destinationService, never()).registerDestination(any(), any(), any(), any());
    }

    @Test
    void wikipediaSourceInsertFailurePropagatesAcrossTransactionalBoundary() throws Exception {
        DestinationForm form = new DestinationForm();
        form.setWikidataQid("Q243");
        DestinationTranslation translation = new DestinationTranslation();
        translation.setId(10L);
        when(destinationService.registerDestination(form, 7L, "WIKIDATA", "Q243")).thenReturn(9L);
        when(destinationMapper.findTranslationByDestinationAndLanguage(9L, "ko"))
                .thenReturn(translation);
        DestinationTranslationSource source = new DestinationTranslationSource();
        doThrow(new IllegalStateException("source insert failed")).when(sourceMapper).insert(source);

        assertThatThrownBy(() -> service.registerWikidataDestination(form, 7L,
                java.util.Map.of("ko", source))).isInstanceOf(IllegalStateException.class);
        assertThat(source.getDestinationTranslationId()).isEqualTo(10L);
        assertThat(DestinationSavePersistenceService.class.getMethod("registerWikidataDestination",
                DestinationForm.class, Long.class, java.util.Map.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void allFiveWikipediaSourcesAttachToTheirOwnTranslationRows() {
        DestinationForm form = new DestinationForm();
        form.setWikidataQid("Q243");
        when(destinationService.registerDestination(form, 7L, "WIKIDATA", "Q243")).thenReturn(9L);
        java.util.Map<String, DestinationTranslationSource> sources = new java.util.LinkedHashMap<>();
        List<String> languages = List.of("ko", "en", "ja", "zh-CN", "zh-TW");
        for (int i = 0; i < languages.size(); i++) {
            DestinationTranslation translation = new DestinationTranslation();
            translation.setId(100L + i);
            when(destinationMapper.findTranslationByDestinationAndLanguage(9L, languages.get(i)))
                    .thenReturn(translation);
            sources.put(languages.get(i), new DestinationTranslationSource());
        }

        assertThat(service.registerWikidataDestination(form, 7L, sources)).isEqualTo(9L);

        for (int i = 0; i < languages.size(); i++) {
            DestinationTranslationSource source = sources.get(languages.get(i));
            assertThat(source.getDestinationTranslationId()).isEqualTo(100L + i);
            verify(sourceMapper).insert(source);
        }
    }

    @Test
    void commonsPhotosSaveImageRowsWithoutLegacySourceColumnsThenFullSourceRows() {
        DestinationForm form = new DestinationForm();
        form.setWikidataQid("Q243");
        when(destinationService.registerDestination(form, 7L, "WIKIDATA", "Q243")).thenReturn(9L);
        PreparedCommonsPhoto main = commonsPhoto("/uploads/destinations/a.jpg", "File:A.jpg", true);
        PreparedCommonsPhoto extra = commonsPhoto("/uploads/destinations/b.jpg", "File:B.jpg", false);
        doAnswer(invocation -> {
            List<DestinationImage> images = invocation.getArgument(1);
            images.get(0).setId(501L);
            images.get(1).setId(502L);
            return null;
        }).when(imageService).saveImages(eq(9L), anyList());

        service.registerWikidataDestination(form, 7L, java.util.Map.of(), List.of(main, extra));

        ArgumentCaptor<List<DestinationImage>> images = ArgumentCaptor.forClass(List.class);
        verify(imageService).saveImages(eq(9L), images.capture());
        assertThat(images.getValue()).extracting(DestinationImage::getSourceType)
                .containsOnly("WIKIMEDIA_COMMONS");
        assertThat(images.getValue()).extracting(DestinationImage::getIsMain).containsExactly(true, false);
        assertThat(images.getValue()).allSatisfy(image -> {
            assertThat(image.getPhotographer()).isNull();
            assertThat(image.getSourceName()).isNull();
            assertThat(image.getLicenseType()).isNull();
            assertThat(image.getSourceImageUrl()).isNull();
        });
        InOrder order = inOrder(imageService, destinationMapper);
        order.verify(imageService).saveImages(eq(9L), anyList());
        order.verify(destinationMapper).insertCommonsImageSource(main.source());
        order.verify(destinationMapper).insertCommonsImageSource(extra.source());
        assertThat(main.source().getDestinationImageId()).isEqualTo(501L);
        assertThat(extra.source().getDestinationImageId()).isEqualTo(502L);
    }

    @Test
    void duplicateCommonsFileForTheSameDestinationIsRefusedBeforeImageInsert() {
        DestinationForm form = new DestinationForm();
        form.setWikidataQid("Q243");
        when(destinationService.registerDestination(form, 7L, "WIKIDATA", "Q243")).thenReturn(9L);

        assertThatThrownBy(() -> service.registerWikidataDestination(form, 7L, java.util.Map.of(), List.of(
                commonsPhoto("/uploads/destinations/a.jpg", "File:A.jpg", true),
                commonsPhoto("/uploads/destinations/b.jpg", "File:A.jpg", false))))
                .isInstanceOf(CommonsPhotoSelectionException.class);

        when(destinationMapper.countCommonsImageSource(9L, "File:C.jpg")).thenReturn(1);
        assertThatThrownBy(() -> service.registerWikidataDestination(form, 7L, java.util.Map.of(), List.of(
                commonsPhoto("/uploads/destinations/c.jpg", "File:C.jpg", true))))
                .isInstanceOf(CommonsPhotoSelectionException.class);
        verify(imageService, never()).saveImages(any(), anyList());
        verify(destinationMapper, never()).insertCommonsImageSource(any());
    }

    @Test
    void commonsSourceInsertFailurePropagatesSoTheWholeRegistrationRollsBack() throws Exception {
        DestinationForm form = new DestinationForm();
        form.setWikidataQid("Q243");
        when(destinationService.registerDestination(form, 7L, "WIKIDATA", "Q243")).thenReturn(9L);
        PreparedCommonsPhoto photo = commonsPhoto("/uploads/destinations/a.jpg", "File:A.jpg", true);
        doAnswer(invocation -> {
            List<DestinationImage> images = invocation.getArgument(1);
            images.get(0).setId(501L);
            return null;
        }).when(imageService).saveImages(eq(9L), anyList());
        doThrow(new org.springframework.dao.DataIntegrityViolationException("source failed"))
                .when(destinationMapper).insertCommonsImageSource(photo.source());

        assertThatThrownBy(() -> service.registerWikidataDestination(
                form, 7L, java.util.Map.of(), List.of(photo)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(DestinationSavePersistenceService.class.getMethod("registerWikidataDestination",
                DestinationForm.class, Long.class, java.util.Map.class, List.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void addingToAnExistingDestinationLocksItThenChecksDuplicatesAndSavesInOneTransaction() throws Exception {
        PreparedCommonsPhoto photo = commonsPhoto("/uploads/destinations/b.jpg", "File:B.jpg", false);
        when(destinationMapper.lockDestinationForImageUpdate(9L)).thenReturn(9L);
        doAnswer(invocation -> {
            invocation.<List<DestinationImage>>getArgument(1).get(0).setId(601L);
            return null;
        }).when(imageService).saveImages(eq(9L), anyList());

        service.addCommonsPhotosToExistingDestination(9L, List.of(photo));

        InOrder order = inOrder(destinationMapper, imageService);
        order.verify(destinationMapper).lockDestinationForImageUpdate(9L);
        order.verify(destinationMapper).countCommonsImageSource(9L, "File:B.jpg");
        order.verify(imageService).saveImages(eq(9L), anyList());
        order.verify(destinationMapper).insertCommonsImageSource(photo.source());
        assertThat(photo.source().getDestinationImageId()).isEqualTo(601L);
        assertThat(DestinationSavePersistenceService.class.getMethod("addCommonsPhotosToExistingDestination",
                Long.class, List.class).getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void concurrentAddOfTheSameFileIsRefusedAfterTheLockAndMissingDestinationSavesNothing() {
        PreparedCommonsPhoto photo = commonsPhoto("/uploads/destinations/b.jpg", "File:B.jpg", false);
        when(destinationMapper.lockDestinationForImageUpdate(9L)).thenReturn(9L);
        // 먼저 잠금을 얻은 요청이 같은 파일을 저장한 뒤라면 여기서 막힌다.
        when(destinationMapper.countCommonsImageSource(9L, "File:B.jpg")).thenReturn(1);

        assertThatThrownBy(() -> service.addCommonsPhotosToExistingDestination(9L, List.of(photo)))
                .isInstanceOf(CommonsPhotoSelectionException.class);
        when(destinationMapper.lockDestinationForImageUpdate(404L)).thenReturn(null);
        assertThatThrownBy(() -> service.addCommonsPhotosToExistingDestination(404L, List.of(photo)))
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(CommonsPhotoSelectionException.class);
        verify(imageService, never()).saveImages(any(), anyList());
        verify(destinationMapper, never()).insertCommonsImageSource(any());
    }

    @Test
    void pixabayPhotosAreSavedAsPixabayImagesWithTheirSourceRowAfterTheLockedDuplicateCheck() throws Exception {
        PreparedPixabayPhoto photo = pixabayPhoto("/uploads/destinations/p.jpg", "195893", true);
        when(destinationMapper.lockDestinationForImageUpdate(9L)).thenReturn(9L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DestinationImage>> images = ArgumentCaptor.forClass(List.class);
        doAnswer(invocation -> {
            invocation.<List<DestinationImage>>getArgument(1).get(0).setId(701L);
            return null;
        }).when(imageService).saveImages(eq(9L), images.capture());

        service.addPixabayPhotosToExistingDestination(9L, List.of(photo));

        InOrder order = inOrder(destinationMapper, imageService);
        order.verify(destinationMapper).lockDestinationForImageUpdate(9L);
        order.verify(destinationMapper).countPixabayImageSource(9L, "195893");
        order.verify(imageService).saveImages(eq(9L), anyList());
        order.verify(destinationMapper).insertCommonsImageSource(photo.source());
        DestinationImage saved = images.getValue().get(0);
        assertThat(saved.getSourceType()).isEqualTo("PIXABAY");
        assertThat(saved.getImageUrl()).isEqualTo("/uploads/destinations/p.jpg");
        assertThat(saved.getIsMain()).isTrue();
        assertThat(photo.source().getDestinationImageId()).isEqualTo(701L);
        assertThat(DestinationSavePersistenceService.class.getMethod("addPixabayPhotosToExistingDestination",
                Long.class, List.class).getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void theSamePixabayPhotoIsRefusedInsideTheTransactionEvenIfTheScreenAllowedIt() {
        when(destinationMapper.lockDestinationForImageUpdate(9L)).thenReturn(9L);
        // 다른 요청이 먼저 같은 사진을 저장했으면 잠금 뒤 재확인에서 막힌다.
        when(destinationMapper.countPixabayImageSource(9L, "195893")).thenReturn(1);
        assertThatThrownBy(() -> service.addPixabayPhotosToExistingDestination(9L,
                List.of(pixabayPhoto("/uploads/destinations/p.jpg", "195893", false))))
                .isInstanceOf(PixabayPhotoException.class)
                .hasMessageContaining("195893");

        // 한 요청 안에 같은 ID가 두 번 들어와도 막는다.
        when(destinationMapper.countPixabayImageSource(9L, "7")).thenReturn(0);
        assertThatThrownBy(() -> service.addPixabayPhotosToExistingDestination(9L, List.of(
                pixabayPhoto("/uploads/destinations/a.jpg", "7", false),
                pixabayPhoto("/uploads/destinations/b.jpg", "7", false))))
                .isInstanceOf(PixabayPhotoException.class);
        verify(imageService, never()).saveImages(any(), anyList());
        verify(destinationMapper, never()).insertCommonsImageSource(any());
    }

    private PreparedPixabayPhoto pixabayPhoto(String localUrl, String pixabayId, boolean main) {
        DestinationImageCommonsSource source = new DestinationImageCommonsSource();
        source.setSourceName("Pixabay");
        source.setExternalContentId(pixabayId);
        return new PreparedPixabayPhoto(localUrl, main, source);
    }

    private PreparedCommonsPhoto commonsPhoto(String localUrl, String title, boolean main) {
        DestinationImageCommonsSource source = new DestinationImageCommonsSource();
        source.setCommonsFileTitle(title);
        source.setSourceTitle(title.substring(5));
        source.setAuthorText("Artist");
        return new PreparedCommonsPhoto(localUrl, main, source);
    }

    @Test
    void registerUsesGeneratedDestinationIdForPreparedKtoPhotos() {
        DestinationForm form = new DestinationForm();
        List<PreparedKtoPhoto> prepared = List.of(prepared());
        when(destinationService.registerDestination(form, 7L, null)).thenReturn(42L);

        service.registerDestination(form, 7L, prepared);

        InOrder order = inOrder(destinationService, ktoPersistenceService);
        // 화면 폼 등록은 외부 contentId 없이(ADMIN 출처로) 저장한다.
        order.verify(destinationService).registerDestination(form, 7L, null);
        order.verify(ktoPersistenceService).persistPhotos(42L, prepared);
    }

    @Test
    void registerWithoutKtoStillUsesTheSameTransactionalMethod() {
        DestinationForm form = new DestinationForm();
        when(destinationService.registerDestination(form, 7L, null)).thenReturn(42L);

        service.registerDestination(form, 7L, List.of());

        verify(ktoPersistenceService).persistPhotos(42L, List.of());
    }

    @Test
    void tourApiRegisterChecksDuplicatesAgainRightBeforeSaving() {
        DestinationForm form = new DestinationForm();
        when(destinationService.existsTourApiDestination("126508")).thenReturn(true);

        assertThatThrownBy(() -> service.registerDestination(form, 7L, "126508", List.of()))
                .isInstanceOf(DuplicateTourApiDestinationException.class);

        verify(destinationService, never()).registerDestination(
                any(DestinationForm.class), any(), any());
        verify(ktoPersistenceService, never()).persistPhotos(any(), any());
    }

    @Test
    void tourApiRegisterSavesWithTheExternalContentId() {
        DestinationForm form = new DestinationForm();
        List<PreparedKtoPhoto> prepared = List.of(prepared());
        when(destinationService.existsTourApiDestination("126508")).thenReturn(false);
        when(destinationService.registerDestination(form, 7L, "126508")).thenReturn(42L);

        assertThat(service.registerDestination(form, 7L, "126508", prepared)).isEqualTo(42L);

        verify(ktoPersistenceService).persistPhotos(42L, prepared);
    }

    @Test
    void tourApiPhotoSaveFailurePropagatesFromTheTransactionBoundary() {
        DestinationForm form = new DestinationForm();
        List<PreparedKtoPhoto> prepared = List.of(prepared());
        when(destinationService.registerDestination(form, 7L, "126508")).thenReturn(42L);
        doThrow(new IllegalStateException("image insert failed"))
                .when(ktoPersistenceService).persistPhotos(42L, prepared);

        assertThatThrownBy(() -> service.registerDestination(form, 7L, "126508", prepared))
                .isInstanceOf(IllegalStateException.class);
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
        Method tourApiRegister = DestinationSavePersistenceService.class.getMethod(
                "registerDestination", DestinationForm.class, Long.class, String.class, List.class);
        Method update = DestinationSavePersistenceService.class.getMethod(
                "updateDestination", Long.class, DestinationForm.class, List.class);

        assertThat(register.getAnnotation(Transactional.class)).isNotNull();
        assertThat(tourApiRegister.getAnnotation(Transactional.class)).isNotNull();
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
