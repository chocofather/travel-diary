package com.example.travlediary.service.destination;

import com.example.travlediary.dto.DestinationDetailDto;
import com.example.travlediary.dto.DestinationForm;
import com.example.travlediary.model.Destination;
import com.example.travlediary.model.DestinationImage;
import com.example.travlediary.model.DestinationSeason;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.repository.bookmark.BookmarkMapper;
import com.example.travlediary.repository.destination.DestinationMapper;
import com.example.travlediary.service.amenity.AmenityService;
import com.example.travlediary.service.comment.DestinationCommentService;
import com.example.travlediary.service.info.AccommodationInfoService;
import com.example.travlediary.service.info.ActivityInfoService;
import com.example.travlediary.service.info.AttractionInfoService;
import com.example.travlediary.service.info.RestaurantInfoService;
import com.example.travlediary.service.info.ShopInfoService;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/** 관리자 폼의 Google Place ID 가 Destination 을 거쳐 Mapper INSERT/UPDATE 까지 전달되는지. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DestinationPlaceIdBindingTest {

    private static final String PLACE_ID = "ChIJ8cM8zdaDGGARDYGSgpooDWw";

    @Mock private DestinationMapper destinationMapper;
    @Mock private BookmarkMapper bookmarkMapper;
    @Mock private AmenityService amenityService;
    @Mock private DestinationCommentService destinationCommentService;
    @Mock private AccommodationInfoService accommodationInfoService;
    @Mock private AttractionInfoService attractionInfoService;
    @Mock private RestaurantInfoService restaurantInfoService;
    @Mock private ActivityInfoService activityInfoService;
    @Mock private ShopInfoService shopInfoService;

    private DestinationService service;

    @TempDir
    Path uploadDir;

    @BeforeEach
    void setUp() {
        DestinationImageService destinationImageService = new DestinationImageService(
                destinationMapper, new FileUploadService(uploadDir.toString()));
        ReflectionTestUtils.setField(destinationImageService, "uploadDir", uploadDir.toString());
        service = new DestinationService(destinationMapper, destinationImageService,
                bookmarkMapper, amenityService,
                destinationCommentService, accommodationInfoService, attractionInfoService,
                restaurantInfoService, activityInfoService, shopInfoService);
        ReflectionTestUtils.setField(service, "uploadPath", uploadDir.toString());
    }

    @Test
    void registerCarriesThePlaceIdIntoTheInsertedDestination() {
        service.registerDestination(form(PLACE_ID), 3L);

        assertThat(insertedDestination().getGooglePlaceId()).isEqualTo(PLACE_ID);
    }

    @Test
    void registerKeepsItNullWhenTheOptionalFieldIsLeftEmpty() {
        service.registerDestination(form(null), 3L);

        assertThat(insertedDestination().getGooglePlaceId()).isNull();
    }

    @Test
    void registerStoresUploadedImagesWithSingleMainAndSequentialOrder() {
        DestinationForm form = form(null);
        form.setMain(true);
        form.setSlide(true);
        form.setImages(new MultipartFile[]{
                new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[]{1}),
                new MockMultipartFile("images", "b.jpg", "image/jpeg", new byte[]{2})
        });

        service.registerDestination(form, 3L);

        ArgumentCaptor<DestinationImage> captor = ArgumentCaptor.forClass(DestinationImage.class);
        verify(destinationMapper, times(2)).insertImage(captor.capture());
        List<DestinationImage> images = captor.getAllValues();
        assertThat(images).extracting(DestinationImage::getIsMain)
                .containsExactly(true, false);
        assertThat(images).extracting(DestinationImage::getIsSlide)
                .containsExactly(true, true);
        assertThat(images).extracting(DestinationImage::getOrderIndex)
                .containsExactly(0, 1);
    }

    @Test
    void updateCarriesThePlaceIdIntoTheUpdatedDestination() {
        Destination stored = new Destination();
        stored.setId(9L);
        when(destinationMapper.findById(9L)).thenReturn(stored);

        service.updateDestination(9L, form(PLACE_ID));

        ArgumentCaptor<Destination> captor = ArgumentCaptor.forClass(Destination.class);
        verify(destinationMapper).updateDestination(captor.capture());
        assertThat(captor.getValue().getGooglePlaceId()).isEqualTo(PLACE_ID);
    }

    @Test
    void editFormIsPrefilledFromTheStoredPlaceId() {
        Destination stored = new Destination();
        stored.setId(9L);
        stored.setGooglePlaceId(PLACE_ID);
        DestinationDetailDto dto = new DestinationDetailDto();
        dto.setDestination(stored);

        assertThat(DestinationForm.fromDetailDto(dto, null).getGooglePlaceId()).isEqualTo(PLACE_ID);
    }

    @Test
    void bothAdminFormsExposeAnOptionalPlaceIdFieldWithHelp() throws IOException {
        for (String template : new String[]{
                "src/main/resources/templates/admin/destinations/create.html",
                "src/main/resources/templates/admin/destinations/edit.html"}) {
            String html = readFile(template);
            assertThat(html).as(template)
                    .contains("th:field=\"*{googlePlaceId}\"")
                    .contains("Google Place ID")
                    .contains("Place ID 찾기")
                    .contains("target=\"_blank\" rel=\"noopener\"");
            // 선택 입력이라 required 를 붙이지 않는다
            assertThat(between(html, "th:field=\"*{googlePlaceId}\"", ">")).doesNotContain("required");
        }
    }

    private Destination insertedDestination() {
        ArgumentCaptor<Destination> captor = ArgumentCaptor.forClass(Destination.class);
        verify(destinationMapper).insertDestination(captor.capture());
        return captor.getValue();
    }

    private DestinationForm form(String googlePlaceId) {
        DestinationForm form = new DestinationForm();
        form.setSeason(DestinationSeason.ALL_SEASONS.name());
        form.setType(DestinationType.ATTRACTION);
        form.setRegionId(201L);
        form.setGooglePlaceId(googlePlaceId);
        form.setImages(new MultipartFile[0]);
        return form;
    }

    private String readFile(String path) throws IOException {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }

    private String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertThat(startIndex).isGreaterThanOrEqualTo(0);
        return source.substring(startIndex, endIndex);
    }
}
