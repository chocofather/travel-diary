package com.tripbora.service.destination;

import com.tripbora.model.DestinationImage;
import com.tripbora.model.DestinationImageCommonsSource;
import com.tripbora.repository.destination.DestinationMapper;
import com.tripbora.service.wikidata.CommonsPhotoImportService;
import com.tripbora.service.wikidata.CommonsPhotoImportService.Selection;
import com.tripbora.service.wikidata.CommonsPhotoSelectionException;
import com.tripbora.service.wikidata.PreparedCommonsPhoto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DestinationCommonsImageManagementServiceTest {

    private static final String JSON = "{\"qid\":\"Q243\",\"photos\":[{\"fileName\":\"B.jpg\",\"main\":false}]}";

    @Mock private CommonsPhotoImportService commons;
    @Mock private DestinationSavePersistenceService persistence;
    @Mock private DestinationImageService images;
    @Mock private DestinationMapper mapper;

    private DestinationCommonsImageManagementService service;

    @BeforeEach
    void setUp() {
        service = new DestinationCommonsImageManagementService(commons, persistence, images, mapper);
    }

    @Test
    void onlyWikidataDestinationsWithAValidQidCanAddCommonsPhotos() {
        when(mapper.findExternalContentIdBySourceType(9L, "WIKIDATA")).thenReturn(null);

        assertThatThrownBy(() -> service.addPhotos(9L, JSON))
                .isInstanceOf(CommonsPhotoSelectionException.class).hasMessageContaining("Wikidata QID");
        verify(commons, never()).prepareForExistingDestination(anyString(), anyList(), anyBoolean());
    }

    @Test
    void existingMainPhotoIsKeptUnlessChosenAndPhotosAreSavedInOneTransaction() {
        List<Selection> selections = List.of(new Selection("B.jpg", false));
        List<PreparedCommonsPhoto> prepared = List.of(photo());
        when(mapper.findExternalContentIdBySourceType(9L, "WIKIDATA")).thenReturn("Q243");
        when(commons.parseSelections(JSON, "Q243")).thenReturn(selections);
        when(images.getImages(9L)).thenReturn(List.of(image(true, null), image(false, "File:A.jpg")));
        when(commons.prepareForExistingDestination("Q243", selections, true)).thenReturn(prepared);

        assertThat(service.addPhotos(9L, JSON)).isEqualTo(1);

        InOrder order = inOrder(commons, persistence);
        order.verify(commons).prepareForExistingDestination("Q243", selections, true);
        order.verify(persistence).addCommonsPhotosToExistingDestination(9L, prepared);
        verify(commons, never()).cleanup(any());
    }

    @Test
    void destinationWithoutImagesLetsTheFirstNewPhotoBecomeMain() {
        List<Selection> selections = List.of(new Selection("B.jpg", false));
        when(mapper.findExternalContentIdBySourceType(9L, "WIKIDATA")).thenReturn("Q243");
        when(commons.parseSelections(JSON, "Q243")).thenReturn(selections);
        when(images.getImages(9L)).thenReturn(List.of());
        when(commons.prepareForExistingDestination("Q243", selections, false)).thenReturn(List.of(photo()));

        service.addPhotos(9L, JSON);

        verify(commons).prepareForExistingDestination("Q243", selections, false);
    }

    @Test
    void alreadyRegisteredFileIsRejectedBeforeRevalidationOrDownload() {
        when(mapper.findExternalContentIdBySourceType(9L, "WIKIDATA")).thenReturn("Q243");
        when(commons.parseSelections(JSON, "Q243")).thenReturn(List.of(new Selection("B.jpg", false)));
        when(images.getImages(9L)).thenReturn(List.of(image(true, "File:B.jpg")));

        assertThatThrownBy(() -> service.addPhotos(9L, JSON))
                .isInstanceOf(CommonsPhotoSelectionException.class).hasMessageContaining("B.jpg");
        verify(commons, never()).prepareForExistingDestination(anyString(), anyList(), anyBoolean());
        verify(persistence, never()).addCommonsPhotosToExistingDestination(any(), any());
    }

    @Test
    void emptySelectionIsRejected() {
        when(mapper.findExternalContentIdBySourceType(9L, "WIKIDATA")).thenReturn("Q243");
        when(commons.parseSelections("", "Q243")).thenReturn(List.of());

        assertThatThrownBy(() -> service.addPhotos(9L, ""))
                .isInstanceOf(CommonsPhotoSelectionException.class).hasMessageContaining("선택");
    }

    @Test
    void dbFailureRemovesThePhotosDownloadedForThisRequest() {
        List<Selection> selections = List.of(new Selection("B.jpg", true));
        List<PreparedCommonsPhoto> prepared = List.of(photo());
        when(mapper.findExternalContentIdBySourceType(9L, "WIKIDATA")).thenReturn("Q243");
        when(commons.parseSelections(JSON, "Q243")).thenReturn(selections);
        when(images.getImages(9L)).thenReturn(List.of(image(true, null)));
        when(commons.prepareForExistingDestination("Q243", selections, true)).thenReturn(prepared);
        doThrow(new DataIntegrityViolationException("insert failed"))
                .when(persistence).addCommonsPhotosToExistingDestination(9L, prepared);

        assertThatThrownBy(() -> service.addPhotos(9L, JSON)).isInstanceOf(DataIntegrityViolationException.class);
        verify(commons).cleanup(prepared);
    }

    @Test
    void registeredFileNamesAreShownWithoutThePrefix() {
        assertThat(service.registeredCommonsFileNames(List.of(image(true, "File:A b.jpg"), image(false, null))))
                .containsExactly("A b.jpg");
    }

    private DestinationImage image(boolean main, String commonsFileTitle) {
        DestinationImage image = new DestinationImage();
        image.setIsMain(main);
        image.setCommonsFileTitle(commonsFileTitle);
        if (commonsFileTitle != null) image.setSourceType("WIKIMEDIA_COMMONS");
        return image;
    }

    private PreparedCommonsPhoto photo() {
        DestinationImageCommonsSource source = new DestinationImageCommonsSource();
        source.setCommonsFileTitle("File:B.jpg");
        return new PreparedCommonsPhoto("/uploads/destinations/b.jpg", false, source);
    }
}
