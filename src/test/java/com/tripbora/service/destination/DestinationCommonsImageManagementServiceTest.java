package com.tripbora.service.destination;

import com.tripbora.model.CountryCategory;
import com.tripbora.model.Destination;
import com.tripbora.model.DestinationImage;
import com.tripbora.model.DestinationImageCommonsSource;
import com.tripbora.model.DestinationTranslation;
import com.tripbora.repository.destination.DestinationMapper;
import com.tripbora.service.category.CountryCategoryService;
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
    private static final String SEARCH_JSON = "{\"source\":\"SEARCH\",\"photos\":[{\"fileName\":\"B.jpg\",\"main\":false}]}";

    @Mock private CommonsPhotoImportService commons;
    @Mock private DestinationSavePersistenceService persistence;
    @Mock private DestinationImageService images;
    @Mock private DestinationMapper mapper;
    @Mock private CountryCategoryService countryCategoryService;

    private DestinationCommonsImageManagementService service;

    @BeforeEach
    void setUp() {
        service = new DestinationCommonsImageManagementService(commons, persistence, images, mapper,
                countryCategoryService);
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

    /** QID가 없는 해외 여행지는 검색으로 고른 사진을 같은 중복 확인·한 트랜잭션 저장 경로로 더한다. */
    @Test
    void searchSelectionOnOverseasDestinationWithoutQidUsesTheSearchRevalidation() {
        List<Selection> selections = List.of(new Selection("B.jpg", false));
        List<PreparedCommonsPhoto> prepared = List.of(photo());
        stubRegion(9L, 31L, path(region(2L, null, 1, "아시아", "Asia"), region(31L, 2L, 2, "말레이시아", "Malaysia")));
        when(commons.isSearchSelection(SEARCH_JSON)).thenReturn(true);
        when(mapper.findExternalContentIdBySourceType(9L, "WIKIDATA")).thenReturn(null);
        when(countryCategoryService.getDomesticRootIds()).thenReturn(List.of(7L));
        when(commons.parseSearchSelections(SEARCH_JSON)).thenReturn(selections);
        when(images.getImages(9L)).thenReturn(List.of(image(true, null)));
        when(commons.prepareSearchResultsForExistingDestination(selections, true)).thenReturn(prepared);

        assertThat(service.addPhotos(9L, SEARCH_JSON)).isEqualTo(1);

        verify(persistence).addCommonsPhotosToExistingDestination(9L, prepared);
        verify(commons, never()).parseSelections(anyString(), anyString());
        verify(commons, never()).prepareForExistingDestination(anyString(), anyList(), anyBoolean());
    }

    @Test
    void searchSelectionIsRejectedForDomesticDestinationsAndDuplicatesAreStoppedFirst() {
        stubRegion(9L, 70L, path(region(7L, null, 1, "대한민국", "Korea"), region(70L, 7L, 2, "서울", "Seoul")));
        when(commons.isSearchSelection(SEARCH_JSON)).thenReturn(true);
        when(countryCategoryService.getDomesticRootIds()).thenReturn(List.of(7L));

        assertThatThrownBy(() -> service.addPhotos(9L, SEARCH_JSON))
                .isInstanceOf(CommonsPhotoSelectionException.class).hasMessageContaining("해외 여행지");
        verify(commons, never()).prepareSearchResultsForExistingDestination(anyList(), anyBoolean());

        // Wikidata 여행지는 검색으로도 더할 수 있고, 이미 있는 파일은 내려받기 전에 막는다.
        when(mapper.findExternalContentIdBySourceType(10L, "WIKIDATA")).thenReturn("Q83063");
        when(commons.parseSearchSelections(SEARCH_JSON)).thenReturn(List.of(new Selection("B.jpg", false)));
        when(images.getImages(10L)).thenReturn(List.of(image(true, "File:B.jpg")));
        assertThatThrownBy(() -> service.addPhotos(10L, SEARCH_JSON))
                .isInstanceOf(CommonsPhotoSelectionException.class).hasMessageContaining("이미 이 여행지에 등록된");
        verify(commons, never()).prepareSearchResultsForExistingDestination(anyList(), anyBoolean());
    }

    /** 해외 여부는 지역의 최상위 루트로 판별하고, 기본 검색어는 영문 이름 + 도시 + 국가다. */
    @Test
    void overseasDestinationGetsEnglishNameCityAndCountryAsTheDefaultQuery() {
        stubRegion(9L, 312L, path(region(2L, null, 1, "아시아", "Asia"), region(31L, 2L, 2, "말레이시아", "Malaysia"),
                region(312L, 31L, 3, "쿠알라룸푸르", "Kuala Lumpur")));
        when(countryCategoryService.getDomesticRootIds()).thenReturn(List.of(7L));
        when(mapper.findTranslationsByDestinationId(9L)).thenReturn(List.of(
                translation("ko", "페트로나스 트윈타워"), translation("en", "Petronas Twin Towers")));

        assertThat(service.isOverseasDestination(9L)).isTrue();
        assertThat(service.defaultSearchQuery(9L)).isEqualTo("Petronas Twin Towers Kuala Lumpur Malaysia");

        // 이름에 이미 도시가 들어 있으면 다시 붙이지 않고, 영문 이름이 없으면 한국어 이름·지역명을 쓴다.
        when(mapper.findTranslationsByDestinationId(9L)).thenReturn(List.of(translation("en", "Kuala Lumpur Tower")));
        assertThat(service.defaultSearchQuery(9L)).isEqualTo("Kuala Lumpur Tower Malaysia");
        when(mapper.findTranslationsByDestinationId(9L)).thenReturn(List.of(translation("ko", "페트로나스 트윈타워")));
        assertThat(service.defaultSearchQuery(9L)).isEqualTo("페트로나스 트윈타워 쿠알라룸푸르 말레이시아");
    }

    @Test
    void domesticOrUnknownRegionKeepsTheKtoScreen() {
        stubRegion(9L, 70L, path(region(7L, null, 1, "대한민국", "Korea"), region(70L, 7L, 2, "서울", "Seoul")));
        when(countryCategoryService.getDomesticRootIds()).thenReturn(List.of(7L));
        when(mapper.findById(10L)).thenReturn(null);

        assertThat(service.isOverseasDestination(9L)).isFalse();
        assertThat(service.isOverseasDestination(10L)).isFalse();
    }

    private void stubRegion(Long destinationId, Long regionId, List<CountryCategory> path) {
        Destination destination = new Destination();
        destination.setRegionId(regionId);
        when(mapper.findById(destinationId)).thenReturn(destination);
        when(countryCategoryService.getRegionPath(regionId)).thenReturn(path);
    }

    private List<CountryCategory> path(CountryCategory... regions) {
        return List.of(regions);
    }

    private CountryCategory region(Long id, Long parentId, int depth, String name, String nameEn) {
        CountryCategory region = new CountryCategory();
        region.setId(id);
        region.setParentId(parentId);
        region.setDepth(depth);
        region.setRegionName(name);
        region.setNameEn(nameEn);
        return region;
    }

    private DestinationTranslation translation(String languageCode, String name) {
        DestinationTranslation translation = new DestinationTranslation();
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        return translation;
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
