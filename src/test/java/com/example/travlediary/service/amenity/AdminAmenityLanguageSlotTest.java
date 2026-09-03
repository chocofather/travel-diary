package com.example.travlediary.service.amenity;

import com.example.travlediary.dto.AmenityForm;
import com.example.travlediary.model.Amenity;
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
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 관리자 편의시설 입력 슬롯은 화면이 읽는 다섯 언어와 같은 코드만 쓴다.
 *
 * <p>ko / en / ja / zh-CN / zh-TW 이며 legacy 'zh' 는 읽지도 쓰지도 않는다.
 * 간체와 번체는 서로 다른 행이라 한쪽을 고쳐도 다른 쪽은 그대로다.
 */
@ExtendWith(MockitoExtension.class)
class AdminAmenityLanguageSlotTest {

    private static final String LEGACY_CHINESE_CODE = "zh";

    @Mock private AmenityMapper amenityMapper;
    @Mock private FileUploadService fileUploadService;
    @InjectMocks private AmenityService amenityService;

    @Test
    void registrationWritesTheFiveCanonicalRowsAndNeverTheLegacyChineseCode() throws IOException {
        doAnswer(invocation -> {
            invocation.getArgument(0, Amenity.class).setId(21);
            return 1;
        }).when(amenityMapper).insertAmenity(any(Amenity.class));
        when(fileUploadService.saveAmenityIcon(anyString(), any()))
                .thenReturn("/uploads/icons/amenities/parking.png");

        amenityService.registerAmenity(fullForm(pngIcon()));

        ArgumentCaptor<AmenityTranslation> captor =
                ArgumentCaptor.forClass(AmenityTranslation.class);
        verify(amenityMapper, org.mockito.Mockito.times(5))
                .insertAmenityTranslation(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AmenityTranslation::getLanguageCode, AmenityTranslation::getName)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("ko", "주차장"),
                        org.assertj.core.api.Assertions.tuple("en", "Parking"),
                        org.assertj.core.api.Assertions.tuple("ja", "駐車場"),
                        org.assertj.core.api.Assertions.tuple("zh-CN", "停车场"),
                        org.assertj.core.api.Assertions.tuple("zh-TW", "停車場"));
        assertThat(captor.getAllValues())
                .extracting(AmenityTranslation::getLanguageCode)
                .doesNotContain(LEGACY_CHINESE_CODE);
        // 편의시설 자체(code/아이콘)는 예전과 같은 흐름이다
        verify(amenityMapper).countByCode("PARKING");
        verify(amenityMapper).updateAmenityIconUrl(21, "/uploads/icons/amenities/parking.png");
    }

    @Test
    void editingOneChineseSlotLeavesTheOtherUntouched() {
        Amenity stored = new Amenity();
        stored.setId(21);
        stored.setCode("PARKING");
        when(amenityMapper.selectAmenityById(21)).thenReturn(stored);
        when(amenityMapper.findTranslation(21, "ko")).thenReturn(translation("ko", "주차장"));
        when(amenityMapper.findTranslation(21, "en")).thenReturn(translation("en", "Parking"));
        when(amenityMapper.findTranslation(21, "ja")).thenReturn(translation("ja", "駐車場"));
        when(amenityMapper.findTranslation(21, "zh-CN")).thenReturn(translation("zh-CN", "停车场"));
        when(amenityMapper.findTranslation(21, "zh-TW")).thenReturn(translation("zh-TW", "停車場"));

        AmenityForm form = fullForm(null);
        form.setId(21);
        form.setNameZhCn("免费停车场");

        amenityService.updateAmenity(form);

        ArgumentCaptor<AmenityTranslation> captor =
                ArgumentCaptor.forClass(AmenityTranslation.class);
        verify(amenityMapper, org.mockito.Mockito.times(5))
                .updateAmenityTranslation(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AmenityTranslation::getLanguageCode, AmenityTranslation::getName)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("ko", "주차장"),
                        org.assertj.core.api.Assertions.tuple("en", "Parking"),
                        org.assertj.core.api.Assertions.tuple("ja", "駐車場"),
                        org.assertj.core.api.Assertions.tuple("zh-CN", "免费停车场"),
                        // 번체는 손대지 않은 값 그대로 다시 저장된다
                        org.assertj.core.api.Assertions.tuple("zh-TW", "停車場"));
        verify(amenityMapper, never()).findTranslation(21, LEGACY_CHINESE_CODE);
        verify(amenityMapper, never()).deleteAmenityTranslation(anyInt(), any());
    }

    @Test
    void aLeftoverLegacyChineseRowNeverFillsEitherChineseSlot() {
        Amenity stored = new Amenity();
        stored.setId(21);
        stored.setCode("PARKING");
        when(amenityMapper.selectAmenityById(21)).thenReturn(stored);
        when(amenityMapper.findTranslationsByAmenityId(21)).thenReturn(List.of(
                translation("ko", "주차장"),
                translation(LEGACY_CHINESE_CODE, "停车场")));

        AmenityForm form = amenityService.getAmenityForm(21);

        assertThat(form.getNameKo()).isEqualTo("주차장");
        assertThat(form.getNameZhCn()).isNull();
        assertThat(form.getNameZhTw()).isNull();
    }

    @Test
    void bothAdminFormsBindTheFiveLanguageSlots() throws IOException {
        for (String path : new String[]{
                "/templates/admin/amenities/create.html",
                "/templates/admin/amenities/edit.html"}) {
            String template = resource(path);
            assertThat(template).as(path)
                    .contains("th:field=\"*{nameKo}\"")
                    .contains("th:field=\"*{nameEn}\"")
                    .contains("th:field=\"*{nameJa}\"")
                    .contains("th:field=\"*{nameZhCn}\"")
                    .contains("th:field=\"*{nameZhTw}\"")
                    // 관리자 화면 라벨은 한국어 그대로다
                    .contains("중국어(간체)")
                    .contains("중국어(번체)")
                    .doesNotContain("*{nameZh}");
        }
    }

    private AmenityForm fullForm(MultipartFile icon) {
        AmenityForm form = new AmenityForm();
        form.setCode("PARKING");
        form.setNameKo("주차장");
        form.setNameEn("Parking");
        form.setNameJa("駐車場");
        form.setNameZhCn("停车场");
        form.setNameZhTw("停車場");
        form.setDestinationTypes(List.of(DestinationType.ATTRACTION));
        form.setIcon(icon);
        return form;
    }

    private AmenityTranslation translation(String languageCode, String name) {
        AmenityTranslation translation = new AmenityTranslation();
        translation.setAmenityId(21);
        translation.setLanguageCode(languageCode);
        translation.setName(name);
        return translation;
    }

    private MultipartFile pngIcon() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB), "png", bytes);
        return new MockMultipartFile("icon", "icon.png", "image/png", bytes.toByteArray());
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
