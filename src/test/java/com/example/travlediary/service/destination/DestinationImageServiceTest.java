package com.example.travlediary.service.destination;

import com.example.travlediary.model.DestinationImage;
import com.example.travlediary.repository.destination.DestinationMapper;
import com.example.travlediary.service.file.FileUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.Invocation;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DestinationImageServiceTest {

    @Mock private DestinationMapper destinationMapper;
    @Mock private FileUploadService fileUploadService;

    @TempDir
    Path uploadDir;

    private DestinationImageService service;

    @BeforeEach
    void setUp() {
        service = new DestinationImageService(destinationMapper, fileUploadService);
        ReflectionTestUtils.setField(service, "uploadDir", uploadDir.toString());
    }

    @Test
    void appendsMultipleImagesAfterCurrentMaximumInOrder() {
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of(
                image(1L, 10L, 1, true),
                image(2L, 10L, 4, false)
        ));
        when(fileUploadService.saveFile(any(), eq("destinations")))
                .thenReturn("/uploads/destinations/new-a.jpg")
                .thenReturn("/uploads/destinations/new-b.jpg");

        service.saveImages(10L, files("a.jpg", "b.jpg"), null, new Integer[0]);

        assertThat(insertedImages())
                .extracting(DestinationImage::getOrderIndex)
                .containsExactly(5, 6);
    }

    @Test
    void selectingNewMainClearsExistingMainAndMarksOnlySelectedImage() {
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of(
                image(1L, 10L, 0, true)
        ));
        when(fileUploadService.saveFile(any(), eq("destinations")))
                .thenReturn("/uploads/destinations/new-a.jpg")
                .thenReturn("/uploads/destinations/new-b.jpg")
                .thenReturn("/uploads/destinations/new-c.jpg");

        service.saveImages(10L, files("a.jpg", "b.jpg", "c.jpg"), 1, new Integer[0]);

        assertThat(invocationsNamed("clearMainImagesByDestinationId"))
                .singleElement()
                .satisfies(invocation -> assertThat((Long) invocation.getArgument(0)).isEqualTo(10L));
        assertThat(insertedImages())
                .extracting(DestinationImage::getIsMain)
                .containsExactly(false, true, false);
    }

    @Test
    void addingImagesWithoutNewMainLeavesExistingMainUntouched() {
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of(
                image(1L, 10L, 3, true)
        ));
        when(fileUploadService.saveFile(any(), eq("destinations")))
                .thenReturn("/uploads/destinations/new.jpg");

        service.saveImages(10L, files("new.jpg"), null, new Integer[0]);

        assertThat(invocationsNamed("clearMainImagesByDestinationId")).isEmpty();
        assertThat(insertedImages())
                .extracting(DestinationImage::getIsMain)
                .containsExactly(false);
    }

    @Test
    void deletingMainImageReordersRemainingImagesAndPromotesFirst() {
        DestinationImage deleted = image(1L, 10L, 0, true);
        when(destinationMapper.findImageById(1L)).thenReturn(deleted);
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of(
                image(2L, 10L, 4, false),
                image(3L, 10L, 8, false)
        ));

        service.deleteImageById(1L);

        assertThat(orderUpdates()).containsExactly(List.of(2L, 0), List.of(3L, 1));
        assertThat(invocationsNamed("setMainImage"))
                .singleElement()
                .satisfies(invocation -> assertThat((Long) invocation.getArgument(0)).isEqualTo(2L));
    }

    @Test
    void deletingOrdinaryImageReordersImagesAndKeepsCurrentMain() {
        DestinationImage deleted = image(2L, 10L, 3, false);
        when(destinationMapper.findImageById(2L)).thenReturn(deleted);
        when(destinationMapper.findImagesByDestinationId(10L)).thenReturn(List.of(
                image(1L, 10L, 2, true),
                image(3L, 10L, 7, false)
        ));

        service.deleteImageById(2L);

        assertThat(orderUpdates()).containsExactly(List.of(1L, 0), List.of(3L, 1));
        assertThat(invocationsNamed("setMainImage")).isEmpty();
        assertThat(invocationsNamed("clearMainImagesByDestinationId")).isEmpty();
    }

    private MultipartFile[] files(String... names) {
        return java.util.Arrays.stream(names)
                .map(name -> new MockMultipartFile("files", name, "image/jpeg", new byte[]{1}))
                .toArray(MultipartFile[]::new);
    }

    private DestinationImage image(Long id, Long destinationId, int orderIndex, boolean main) {
        DestinationImage image = new DestinationImage();
        image.setId(id);
        image.setDestinationId(destinationId);
        image.setImageUrl("/uploads/destinations/" + id + ".jpg");
        image.setOrderIndex(orderIndex);
        image.setIsMain(main);
        image.setIsSlide(false);
        return image;
    }

    private List<DestinationImage> insertedImages() {
        ArgumentCaptor<DestinationImage> captor = ArgumentCaptor.forClass(DestinationImage.class);
        verify(destinationMapper, org.mockito.Mockito.atLeastOnce()).insertImage(captor.capture());
        return captor.getAllValues();
    }

    private List<Invocation> invocationsNamed(String methodName) {
        return mockingDetails(destinationMapper).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals(methodName))
                .toList();
    }

    private List<List<Object>> orderUpdates() {
        return invocationsNamed("updateImageOrder").stream()
                .map(invocation -> List.of(invocation.getArgument(0), invocation.getArgument(1)))
                .toList();
    }
}
