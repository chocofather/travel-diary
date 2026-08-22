package com.example.travlediary.service.amenity;

import com.example.travlediary.dto.AmenityForm;
import com.example.travlediary.model.Amenity;
import com.example.travlediary.model.AmenityTranslation;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.repository.amenity.AmenityMapper;
import com.example.travlediary.service.file.FileUploadService;
import com.example.travlediary.service.file.UnsupportedImageFormatException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 통합 편의시설 등록: 입력 검증을 모두 끝낸 뒤 DB 를 쓰고, 아이콘 파일은 마지막에 저장한다.
 */
@ExtendWith(MockitoExtension.class)
class AmenityRegistrationServiceTest {

    @Mock
    private AmenityMapper amenityMapper;
    @Mock
    private FileUploadService fileUploadService;
    @InjectMocks
    private AmenityService amenityService;

    @Test
    void savesCodeTranslationsTypesAndIconInThatOrder() throws Exception {
        givenGeneratedId(7);
        givenIconSavedAs("/uploads/icons/amenities/free_wifi.png");
        MultipartFile icon = pngIcon();

        Integer amenityId = amenityService.registerAmenity(form(icon,
                List.of(DestinationType.CAFE, DestinationType.ACCOMMODATION)));

        assertThat(amenityId).isEqualTo(7);
        verify(amenityMapper).countByCode("FREE_WIFI");
        // 아이콘은 DB 를 건드리기 전에 검증만 한다
        verify(fileUploadService).validateAmenityIcon(icon);

        ArgumentCaptor<Amenity> amenity = ArgumentCaptor.forClass(Amenity.class);
        verify(amenityMapper).insertAmenity(amenity.capture());
        assertThat(amenity.getValue().getCode()).isEqualTo("FREE_WIFI");

        assertThat(insertedTranslations())
                .containsExactly(
                        tuple(7, "ko", "무료 와이파이"),
                        tuple(7, "en", "Free Wi-Fi"),
                        tuple(7, "ja", "無料Wi-Fi"),
                        tuple(7, "zh", "免费Wi-Fi"));

        verify(amenityMapper).insertAmenityDestinationType(7, "CAFE");
        verify(amenityMapper).insertAmenityDestinationType(7, "ACCOMMODATION");
        verify(fileUploadService).saveAmenityIcon("FREE_WIFI", icon);
        verify(amenityMapper).updateAmenityIconUrl(7, "/uploads/icons/amenities/free_wifi.png");
    }

    @Test
    void recordsWhateverIconUrlTheStorageReturnedIncludingSvg() throws Exception {
        givenGeneratedId(4);
        givenIconSavedAs("/uploads/icons/amenities/free_wifi.svg");

        amenityService.registerAmenity(form(pngIcon(), List.of(DestinationType.CAFE)));

        verify(amenityMapper).updateAmenityIconUrl(4, "/uploads/icons/amenities/free_wifi.svg");
    }

    @Test
    void propagatesAnIconUrlUpdateFailureSoTheTransactionCanRollBack() throws Exception {
        givenGeneratedId(6);
        givenIconSavedAs("/uploads/icons/amenities/free_wifi.jpg");
        doThrow(new IllegalStateException("update 실패"))
                .when(amenityMapper).updateAmenityIconUrl(anyInt(), anyString());

        assertThatThrownBy(() -> amenityService.registerAmenity(
                form(pngIcon(), List.of(DestinationType.CAFE))))
                .isInstanceOf(IllegalStateException.class);

        // 파일은 이미 저장됐으므로 롤백 정리가 등록되어 있어야 한다
        verify(fileUploadService).saveAmenityIcon(anyString(), any());
    }

    @Test
    void skipsBlankOptionalTranslationsAndTrimsTheRest() throws Exception {
        givenGeneratedId(11);
        AmenityForm form = form(pngIcon(), List.of(DestinationType.SHOP));
        form.setCode("  FREE_WIFI  ");
        form.setNameKo("  무료 와이파이  ");
        form.setNameEn("   ");
        form.setNameJa("");
        form.setNameZh(null);

        amenityService.registerAmenity(form);

        assertThat(insertedTranslations()).containsExactly(tuple(11, "ko", "무료 와이파이"));
        verify(amenityMapper).countByCode("FREE_WIFI");
        verify(fileUploadService).saveAmenityIcon("FREE_WIFI", form.getIcon());
    }

    @Test
    void insertsEachDestinationTypeOnlyOnceEvenWhenSubmittedTwice() throws Exception {
        givenGeneratedId(3);

        amenityService.registerAmenity(form(pngIcon(), List.of(
                DestinationType.CAFE, DestinationType.CAFE, DestinationType.RESTAURANTS)));

        verify(amenityMapper, times(1)).insertAmenityDestinationType(3, "CAFE");
        verify(amenityMapper, times(1)).insertAmenityDestinationType(3, "RESTAURANTS");
        verify(amenityMapper, times(2)).insertAmenityDestinationType(anyInt(), anyString());
    }

    @Test
    void rejectsAMalformedCodeBeforeTouchingAnything() throws Exception {
        for (String code : new String[]{null, "", "  ", "free_wifi", "FREE WIFI", "FREE-WIFI", "A"}) {
            AmenityForm form = form(pngIcon(), List.of(DestinationType.CAFE));
            form.setCode(code);

            assertThatThrownBy(() -> amenityService.registerAmenity(form))
                    .as("code=%s", code)
                    .isInstanceOf(AmenityValidationException.class)
                    .extracting("field").isEqualTo("code");
        }
        verifyNoInteractions(amenityMapper);
    }

    @Test
    void rejectsADuplicateCodeWithoutInserting() throws Exception {
        when(amenityMapper.countByCode("FREE_WIFI")).thenReturn(1);

        assertThatThrownBy(() -> amenityService.registerAmenity(
                form(pngIcon(), List.of(DestinationType.CAFE))))
                .isInstanceOf(AmenityValidationException.class)
                .hasMessageContaining("이미 등록된")
                .extracting("field").isEqualTo("code");

        verify(amenityMapper, never()).insertAmenity(any());
        verify(fileUploadService, never()).saveAmenityIcon(anyString(), any());
    }

    @Test
    void rejectsABlankKoreanName() throws Exception {
        AmenityForm form = form(pngIcon(), List.of(DestinationType.CAFE));
        form.setNameKo("  ");

        assertThatThrownBy(() -> amenityService.registerAmenity(form))
                .isInstanceOf(AmenityValidationException.class)
                .extracting("field").isEqualTo("nameKo");
        verifyNoInteractions(amenityMapper);
    }

    @Test
    void rejectsNamesLongerThanTheColumn() throws Exception {
        AmenityForm tooLongKorean = form(pngIcon(), List.of(DestinationType.CAFE));
        tooLongKorean.setNameKo("가".repeat(101));
        assertThatThrownBy(() -> amenityService.registerAmenity(tooLongKorean))
                .isInstanceOf(AmenityValidationException.class)
                .extracting("field").isEqualTo("nameKo");

        AmenityForm tooLongEnglish = form(pngIcon(), List.of(DestinationType.CAFE));
        tooLongEnglish.setNameEn("a".repeat(101));
        assertThatThrownBy(() -> amenityService.registerAmenity(tooLongEnglish))
                .isInstanceOf(AmenityValidationException.class)
                .extracting("field").isEqualTo("nameEn");

        verifyNoInteractions(amenityMapper);
    }

    @Test
    void rejectsAnEmptyDestinationTypeSelection() throws Exception {
        AmenityForm empty = form(pngIcon(), List.of());
        assertThatThrownBy(() -> amenityService.registerAmenity(empty))
                .isInstanceOf(AmenityValidationException.class)
                .extracting("field").isEqualTo("destinationTypes");

        AmenityForm nullTypes = form(pngIcon(), List.of());
        nullTypes.setDestinationTypes(null);
        assertThatThrownBy(() -> amenityService.registerAmenity(nullTypes))
                .isInstanceOf(AmenityValidationException.class)
                .extracting("field").isEqualTo("destinationTypes");

        verifyNoInteractions(amenityMapper);
    }

    @Test
    void rejectsAMissingIcon() {
        AmenityForm missing = form(null, List.of(DestinationType.CAFE));
        assertThatThrownBy(() -> amenityService.registerAmenity(missing))
                .isInstanceOf(AmenityValidationException.class)
                .extracting("field").isEqualTo("icon");

        AmenityForm empty = form(
                new MockMultipartFile("icon", "icon.png", "image/png", new byte[0]),
                List.of(DestinationType.CAFE));
        assertThatThrownBy(() -> amenityService.registerAmenity(empty))
                .isInstanceOf(AmenityValidationException.class)
                .extracting("field").isEqualTo("icon");

        verifyNoInteractions(amenityMapper);
    }

    @Test
    void propagatesIconValidationFailuresBeforeAnyInsert() throws Exception {
        doThrow(new UnsupportedImageFormatException("PNG 이미지 파일만 업로드할 수 있습니다."))
                .when(fileUploadService).validateAmenityIcon(any());

        assertThatThrownBy(() -> amenityService.registerAmenity(
                form(pngIcon(), List.of(DestinationType.CAFE))))
                .isInstanceOf(UnsupportedImageFormatException.class);

        verifyNoInteractions(amenityMapper);
    }

    @Test
    void propagatesIconSaveFailuresSoTheTransactionCanRollBack() throws Exception {
        givenGeneratedId(5);
        doThrow(new IllegalStateException("같은 코드의 아이콘 파일이 이미 있습니다."))
                .when(fileUploadService).saveAmenityIcon(anyString(), any());

        assertThatThrownBy(() -> amenityService.registerAmenity(
                form(pngIcon(), List.of(DestinationType.CAFE))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 있습니다");

        // DB 쓰기는 이미 일어났고, 예외가 그대로 올라가야 트랜잭션이 롤백된다
        verify(amenityMapper).insertAmenity(any());
    }

    @Test
    void registersACleanupThatDeletesTheNewIconOnlyWhenTheTransactionDoesNotCommit()
            throws Exception {
        givenGeneratedId(9);
        TransactionSynchronizationManager.initSynchronization();
        try {
            amenityService.registerAmenity(form(pngIcon(), List.of(DestinationType.CAFE)));
            List<TransactionSynchronization> registered =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(registered).hasSize(1);

            registered.get(0).afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            verify(fileUploadService, never()).deleteAmenityIcon(anyString());

            registered.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(fileUploadService).deleteAmenityIcon("FREE_WIFI");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void cleanupFailureDoesNotEscapeTheRollback() throws Exception {
        givenGeneratedId(9);
        doThrow(new IllegalStateException("삭제 실패"))
                .when(fileUploadService).deleteAmenityIcon(anyString());
        TransactionSynchronizationManager.initSynchronization();
        try {
            amenityService.registerAmenity(form(pngIcon(), List.of(DestinationType.CAFE)));
            TransactionSynchronization cleanup =
                    TransactionSynchronizationManager.getSynchronizations().get(0);

            // 정리 실패는 warn 로그로만 남기고 원래 롤백 흐름을 덮지 않는다
            cleanup.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void givenGeneratedId(int generatedId) {
        doAnswer(invocation -> {
            invocation.getArgument(0, Amenity.class).setId(generatedId);
            return 1;
        }).when(amenityMapper).insertAmenity(any(Amenity.class));
    }

    private void givenIconSavedAs(String iconUrl) {
        when(fileUploadService.saveAmenityIcon(anyString(), any())).thenReturn(iconUrl);
    }

    private AmenityForm form(MultipartFile icon, List<DestinationType> types) {
        AmenityForm form = new AmenityForm();
        form.setCode("FREE_WIFI");
        form.setNameKo("무료 와이파이");
        form.setNameEn("Free Wi-Fi");
        form.setNameJa("無料Wi-Fi");
        form.setNameZh("免费Wi-Fi");
        form.setDestinationTypes(types);
        form.setIcon(icon);
        return form;
    }

    private List<org.assertj.core.groups.Tuple> insertedTranslations() {
        ArgumentCaptor<AmenityTranslation> captor =
                ArgumentCaptor.forClass(AmenityTranslation.class);
        verify(amenityMapper, org.mockito.Mockito.atLeastOnce())
                .insertAmenityTranslation(captor.capture());
        return captor.getAllValues().stream()
                .map(translation -> tuple(
                        translation.getAmenityId(),
                        translation.getLanguageCode(),
                        translation.getName()))
                .toList();
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.api.Assertions.tuple(values);
    }

    private MultipartFile pngIcon() throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "png", bytes)).isTrue();
        return new MockMultipartFile("icon", "icon.png", "image/png", bytes.toByteArray());
    }
}
