package com.example.travlediary.service.amenity;

import com.example.travlediary.dto.AmenityForm;
import com.example.travlediary.model.Amenity;
import com.example.travlediary.model.AmenityDestinationType;
import com.example.travlediary.model.AmenityTranslation;
import com.example.travlediary.model.DestinationType;
import com.example.travlediary.repository.amenity.AmenityMapper;
import com.example.travlediary.service.file.FileUploadService;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 편의시설 수정: code 는 절대 바뀌지 않고, 아이콘은 새로 고른 경우에만 교체한다.
 */
@ExtendWith(MockitoExtension.class)
class AmenityUpdateServiceTest {

    @Mock
    private AmenityMapper amenityMapper;
    @Mock
    private FileUploadService fileUploadService;
    @InjectMocks
    private AmenityService amenityService;

    @Test
    void editFormRestoresCodeEveryLanguageAndCheckedTypes() {
        givenStoredAmenity("/uploads/icons/amenities/slippers.png");
        when(amenityMapper.findTranslationsByAmenityId(3)).thenReturn(List.of(
                translation("ko", "슬리퍼"), translation("en", "Slippers"), translation("ja", "スリッパ")));
        when(amenityMapper.findAmenityDestinationTypesByAmenityId(3)).thenReturn(List.of(
                mapping("ACCOMMODATION"), mapping("CAFE")));

        AmenityForm form = amenityService.getAmenityForm(3);

        assertThat(form.getId()).isEqualTo(3);
        assertThat(form.getCode()).isEqualTo("SLIPPERS");
        assertThat(form.getNameKo()).isEqualTo("슬리퍼");
        assertThat(form.getNameEn()).isEqualTo("Slippers");
        assertThat(form.getNameJa()).isEqualTo("スリッパ");
        assertThat(form.getNameZh()).isNull();
        assertThat(form.getDestinationTypes())
                .containsExactly(DestinationType.ACCOMMODATION, DestinationType.CAFE);
    }

    @Test
    void editPreviewUsesIconUrlAndFallsBackToTheLegacyPngPath() {
        givenStoredAmenity("/uploads/icons/amenities/slippers.svg");
        assertThat(amenityService.getAmenityIconUrl(3))
                .isEqualTo("/uploads/icons/amenities/slippers.svg");

        givenStoredAmenity(null);
        assertThat(amenityService.getAmenityIconUrl(3))
                .isEqualTo("/uploads/icons/amenities/slippers.png");

        givenStoredAmenity("   ");
        assertThat(amenityService.getAmenityIconUrl(3))
                .isEqualTo("/uploads/icons/amenities/slippers.png");
    }

    @Test
    void updatesTranslationsAndTypesWithoutTouchingCodeOrTheIcon() {
        givenStoredAmenity("/uploads/icons/amenities/slippers.png");
        when(amenityMapper.findTranslation(3, "ko")).thenReturn(translation("ko", "슬리퍼"));
        when(amenityMapper.findTranslation(3, "en")).thenReturn(null);
        when(amenityMapper.findTranslation(3, "ja")).thenReturn(translation("ja", "スリッパ"));
        when(amenityMapper.findTranslation(3, "zh")).thenReturn(null);

        AmenityForm form = form(null, List.of(DestinationType.ACCOMMODATION, DestinationType.CAFE));
        form.setCode("HACKED_CODE");
        form.setNameKo("실내 슬리퍼");
        form.setNameEn("Indoor Slippers");
        form.setNameJa("   ");   // 값을 비우면 기존 번역을 지운다
        form.setNameZh(null);

        amenityService.updateAmenity(form);

        // 값 있음 + 기존 행 있음 -> UPDATE
        assertThat(updatedTranslations()).containsExactly(tuple(3, "ko", "실내 슬리퍼"));
        // 값 있음 + 기존 행 없음 -> INSERT
        assertThat(insertedTranslations()).containsExactly(tuple(3, "en", "Indoor Slippers"));
        // 값 없음 + 기존 행 있음 -> DELETE
        verify(amenityMapper).deleteAmenityTranslation(3, "ja");
        // 값 없음 + 기존 행 없음 -> 아무것도 하지 않는다
        verify(amenityMapper, never()).deleteAmenityTranslation(3, "zh");

        // 적용 대상은 전체 삭제 후 재삽입
        verify(amenityMapper).deleteAmenityDestinationTypesByAmenityId(3);
        verify(amenityMapper).insertAmenityDestinationType(3, "ACCOMMODATION");
        verify(amenityMapper).insertAmenityDestinationType(3, "CAFE");

        // code 는 폼 값과 상관없이 절대 건드리지 않는다
        verify(amenityMapper, never()).insertAmenity(any());
        // 아이콘 미선택 -> 파일도 icon_url 도 그대로
        verifyNoInteractions(fileUploadService);
        verify(amenityMapper, never()).updateAmenityIconUrl(anyInt(), anyString());
    }

    @Test
    void replacesTheIconAndRecordsTheNewExtensionWhenANewFileIsChosen() {
        givenStoredAmenity("/uploads/icons/amenities/slippers.png");
        givenNoExistingTranslations();
        MultipartFile icon = svgIcon();
        when(fileUploadService.replaceAmenityIcon(eq("SLIPPERS"), any()))
                .thenReturn(replacement("/uploads/icons/amenities/slippers.svg", "svg"));

        amenityService.updateAmenity(form(icon, List.of(DestinationType.SHOP)));

        // DB 를 건드리기 전에 파일 검증부터 한다
        verify(fileUploadService).validateAmenityIcon(icon);
        verify(fileUploadService).replaceAmenityIcon("SLIPPERS", icon);
        verify(amenityMapper).updateAmenityIconUrl(3, "/uploads/icons/amenities/slippers.svg");
    }

    @Test
    void insertsEachTypeOnlyOnceEvenWhenSubmittedTwice() {
        givenStoredAmenity(null);
        givenNoExistingTranslations();

        amenityService.updateAmenity(form(null, List.of(
                DestinationType.CAFE, DestinationType.CAFE, DestinationType.RESTAURANTS)));

        verify(amenityMapper).insertAmenityDestinationType(3, "CAFE");
        verify(amenityMapper).insertAmenityDestinationType(3, "RESTAURANTS");
        verify(amenityMapper, org.mockito.Mockito.times(2))
                .insertAmenityDestinationType(anyInt(), anyString());
    }

    @Test
    void rejectsInvalidInputBeforeWritingAnything() {
        givenStoredAmenity(null);

        AmenityForm blankKorean = form(null, List.of(DestinationType.CAFE));
        blankKorean.setNameKo("  ");
        assertThatThrownBy(() -> amenityService.updateAmenity(blankKorean))
                .isInstanceOf(AmenityValidationException.class)
                .extracting("field").isEqualTo("nameKo");

        AmenityForm tooLong = form(null, List.of(DestinationType.CAFE));
        tooLong.setNameEn("a".repeat(101));
        assertThatThrownBy(() -> amenityService.updateAmenity(tooLong))
                .isInstanceOf(AmenityValidationException.class)
                .extracting("field").isEqualTo("nameEn");

        AmenityForm noTypes = form(null, List.of());
        assertThatThrownBy(() -> amenityService.updateAmenity(noTypes))
                .isInstanceOf(AmenityValidationException.class)
                .extracting("field").isEqualTo("destinationTypes");

        verify(amenityMapper, never()).deleteAmenityDestinationTypesByAmenityId(anyInt());
        verify(amenityMapper, never()).updateAmenityTranslation(any());
        verifyNoInteractions(fileUploadService);
    }

    @Test
    void rejectsAnUnknownAmenity() {
        when(amenityMapper.selectAmenityById(99)).thenReturn(null);

        AmenityForm form = form(null, List.of(DestinationType.CAFE));
        form.setId(99);
        assertThatThrownBy(() -> amenityService.updateAmenity(form))
                .isInstanceOf(AmenityValidationException.class)
                .extracting("field").isEqualTo("id");
    }

    @Test
    void keepsTheOldIconWhenSavingTheNewOneFails() {
        givenStoredAmenity("/uploads/icons/amenities/slippers.png");
        givenNoExistingTranslations();
        when(fileUploadService.replaceAmenityIcon(anyString(), any()))
                .thenThrow(new IllegalStateException("편의시설 아이콘 파일 교체에 실패했습니다."));

        assertThatThrownBy(() -> amenityService.updateAmenity(
                form(svgIcon(), List.of(DestinationType.SHOP))))
                .isInstanceOf(IllegalStateException.class);

        // 예외가 그대로 올라가 트랜잭션이 롤백되고, 기존 아이콘은 손대지 않았다
        verify(amenityMapper, never()).updateAmenityIconUrl(anyInt(), anyString());
        verify(fileUploadService, never()).commitAmenityIconReplacement(any());
    }

    @Test
    void commitsTheReplacementOnlyAfterTheTransactionCommits() {
        givenStoredAmenity("/uploads/icons/amenities/slippers.png");
        givenNoExistingTranslations();
        FileUploadService.AmenityIconReplacement replacement =
                replacement("/uploads/icons/amenities/slippers.svg", "svg");
        when(fileUploadService.replaceAmenityIcon(anyString(), any())).thenReturn(replacement);

        TransactionSynchronizationManager.initSynchronization();
        try {
            amenityService.updateAmenity(form(svgIcon(), List.of(DestinationType.SHOP)));
            List<TransactionSynchronization> registered =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(registered).hasSize(1);

            registered.get(0).afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            verify(fileUploadService).commitAmenityIconReplacement(replacement);
            verify(fileUploadService, never()).rollbackAmenityIconReplacement(any());

            registered.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(fileUploadService).rollbackAmenityIconReplacement(replacement);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void givenStoredAmenity(String iconUrl) {
        Amenity amenity = new Amenity();
        amenity.setId(3);
        amenity.setCode("SLIPPERS");
        amenity.setIconUrl(iconUrl);
        when(amenityMapper.selectAmenityById(3)).thenReturn(amenity);
    }

    private void givenNoExistingTranslations() {
        when(amenityMapper.findTranslation(anyInt(), anyString())).thenReturn(null);
    }

    private FileUploadService.AmenityIconReplacement replacement(String iconUrl, String extension) {
        return new FileUploadService.AmenityIconReplacement(
                iconUrl, "SLIPPERS", extension, Path.of("slippers." + extension), null);
    }

    private AmenityForm form(MultipartFile icon, List<DestinationType> types) {
        AmenityForm form = new AmenityForm();
        form.setId(3);
        form.setCode("SLIPPERS");
        form.setNameKo("슬리퍼");
        form.setDestinationTypes(types);
        form.setIcon(icon);
        return form;
    }

    private MultipartFile svgIcon() {
        return new MockMultipartFile("icon", "icon.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8));
    }

    private AmenityTranslation translation(String languageCode, String name) {
        AmenityTranslation translation = new AmenityTranslation();
        translation.setAmenityId(3);
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        return translation;
    }

    private AmenityDestinationType mapping(String destinationType) {
        AmenityDestinationType mapping = new AmenityDestinationType();
        mapping.setAmenityId(3);
        mapping.setDestinationType(destinationType);
        return mapping;
    }

    private List<org.assertj.core.groups.Tuple> updatedTranslations() {
        ArgumentCaptor<AmenityTranslation> captor =
                ArgumentCaptor.forClass(AmenityTranslation.class);
        verify(amenityMapper, org.mockito.Mockito.atLeastOnce())
                .updateAmenityTranslation(captor.capture());
        return captor.getAllValues().stream().map(this::tupleOf).toList();
    }

    private List<org.assertj.core.groups.Tuple> insertedTranslations() {
        ArgumentCaptor<AmenityTranslation> captor =
                ArgumentCaptor.forClass(AmenityTranslation.class);
        verify(amenityMapper, org.mockito.Mockito.atLeastOnce())
                .insertAmenityTranslation(captor.capture());
        return captor.getAllValues().stream().map(this::tupleOf).toList();
    }

    private org.assertj.core.groups.Tuple tupleOf(AmenityTranslation translation) {
        return tuple(translation.getAmenityId(), translation.getLanguageCode(), translation.getName());
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.api.Assertions.tuple(values);
    }
}
